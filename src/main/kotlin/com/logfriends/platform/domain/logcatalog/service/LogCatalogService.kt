package com.logfriends.platform.domain.logcatalog.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.logfriends.platform.api.dto.*
import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEvent
import com.logfriends.platform.domain.discoveredlogevent.repository.DiscoveredLogEventRepository
import com.logfriends.platform.domain.fieldrequest.entity.FieldRequestStatus
import com.logfriends.platform.domain.fieldrequest.entity.FieldType
import com.logfriends.platform.domain.fieldrequest.repository.FieldRequestRepository
import com.logfriends.platform.domain.logspec.entity.LogSpecSnapshot
import com.logfriends.platform.domain.logspec.repository.LogSpecSnapshotRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant

@Service
class LogCatalogService(
    private val agentRepository: AgentRepository,
    private val logSpecSnapshotRepository: LogSpecSnapshotRepository,
    private val discoveredLogEventRepository: DiscoveredLogEventRepository,
    private val fieldRequestRepository: FieldRequestRepository,
    private val payloadNormalizer: LogCatalogPayloadNormalizer,
    private val mismatchCalculator: LogCatalogMismatchCalculator,
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) {

    fun listApps(): LogCatalogAppsResponse {
        val apps = agentRepository.findAll()
            .groupBy { it.appName }
            .map { (appName, agents) ->
                LogCatalogAppResponse(
                    appName = appName,
                    workerIds = agents.map { it.workerId }.distinct().sorted()
                )
            }
            .filter { it.workerIds.isNotEmpty() }
            .sortedBy { it.appName }

        return LogCatalogAppsResponse(apps)
    }

    fun listEvents(appName: String, workerId: String?, sampleSize: Int?): LogCatalogEventsResponse {
        val agents = agentsForApp(appName)
        val workerIds = agents.map { it.workerId }.distinct().sorted()
        val selectedWorkerId = workerId?.trim()?.takeIf { it.isNotBlank() }

        if (selectedWorkerId != null && selectedWorkerId !in workerIds) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "workerId does not belong to appName")
        }

        val targetWorkerIds = selectedWorkerId?.let { listOf(it) } ?: workerIds
        val targetAgents = selectedWorkerId
            ?.let { worker -> agents.filter { it.workerId == worker } }
            ?: agents
        val discoveredHintsByEventName = fetchDiscoveredHints(targetAgents).groupBy { it.eventName }
        val specs = logSpecSnapshotRepository.findAllByAppName(appName)
            .groupBy { it.specName }
            .mapValues { (_, grouped) -> grouped.maxBy { it.updatedAt } }
        val samples = fetchSamples(targetWorkerIds, normalizeSampleSize(sampleSize))
        val samplesByEventName = samples.groupBy { it.eventName }
        val eventNames = (specs.keys + samplesByEventName.keys + discoveredHintsByEventName.keys).sorted()
        val requestsByEventName = if (eventNames.isEmpty()) {
            emptyMap()
        } else {
            fieldRequestRepository.findAllByAppNameAndEventNameIn(appName, eventNames)
                .sortedWith(fieldRequestComparator())
                .groupBy { it.eventName }
        }

        val events = eventNames.map { eventName ->
            val spec = specs[eventName]
            val eventSamples = samplesByEventName[eventName].orEmpty()
            val fields = spec?.fields.orEmpty().mapNotNull { toFieldResponse(it) }
            val latestPayload = eventSamples.firstOrNull()?.payload.orEmpty()
            val comparablePayload = payloadNormalizer.normalizeForFieldComparison(latestPayload)

            LogCatalogEventResponse(
                eventName = eventName,
                description = spec?.description,
                apiContext = spec?.toApiContextResponse(),
                specStatus = when {
                    spec == null -> LogCatalogSpecStatus.NO_SPEC
                    eventSamples.isEmpty() -> LogCatalogSpecStatus.NO_SAMPLE
                    else -> LogCatalogSpecStatus.REGISTERED
                },
                discoveredHints = discoveredHintsByEventName[eventName].orEmpty().map { it.toDiscoveredHintResponse() },
                fields = fields,
                samples = eventSamples.map {
                    LogCatalogSampleResponse(
                        workerId = it.workerId,
                        ts = it.ts,
                        payload = maskPayload(it.payload)
                    )
                },
                mismatches = mismatchCalculator.calculate(fields, comparablePayload),
                fieldRequests = requestsByEventName[eventName].orEmpty().map { FieldRequestResponse.from(it) }
            )
        }

        return LogCatalogEventsResponse(
            appName = appName,
            selectedWorkerId = selectedWorkerId,
            workerIds = workerIds,
            eventTypeSummaries = fetchEventTypeSummaries(appName, selectedWorkerId),
            failureSummaries = fetchFailureSummaries(targetWorkerIds),
            events = events
        )
    }

    private fun LogSpecSnapshot.toApiContextResponse(): LogCatalogApiContextResponse? {
        val method = apiMethod?.trim()?.takeIf { it.isNotBlank() }
        val path = apiPath?.trim()?.takeIf { it.isNotBlank() }
        val description = apiDescription?.trim()?.takeIf { it.isNotBlank() }
        if (method == null && path == null && description == null) return null
        return LogCatalogApiContextResponse(method, path, description)
    }

    private fun fetchDiscoveredHints(agents: List<Agent>): List<DiscoveredLogEvent> {
        val agentIds = agents.mapNotNull { it.id }
        if (agentIds.isEmpty()) return emptyList()
        return discoveredLogEventRepository.findAllByAgentIdInOrderByEventNameAscSourceClassAscSourceMethodAsc(agentIds)
    }

    private fun DiscoveredLogEvent.toDiscoveredHintResponse(): LogCatalogDiscoveredHintResponse =
        LogCatalogDiscoveredHintResponse(
            sourceClass = sourceClass,
            sourceMethod = sourceMethod,
            appVersion = appVersion,
            specHint = specHint
        )

    private fun agentsForApp(appName: String): List<Agent> {
        val normalized = appName.trim()
        if (normalized.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "appName is required")
        }

        val agents = agentRepository.findByAppName(normalized)
        if (agents.isEmpty()) {
            throw BusinessException(ErrorCode.NOT_FOUND, "appName not found")
        }
        return agents
    }

    private fun fetchSamples(workerIds: List<String>, sampleSize: Int): List<CustomEventSample> {
        if (workerIds.isEmpty()) return emptyList()

        return dsl.resultQuery(
            """
            SELECT event_name, worker_id, ts, payload
            FROM (
                SELECT event_name, worker_id, ts, payload,
                       ROW_NUMBER() OVER (PARTITION BY event_name ORDER BY ts DESC, id DESC) AS rn
                FROM custom_events
                WHERE worker_id IN ({0})
            ) ranked
            WHERE rn <= {1}
            ORDER BY event_name ASC, ts DESC, worker_id ASC
            """.trimIndent(),
            DSL.list(workerIds.map { DSL.inline(it) }),
            sampleSize
        ).fetch { record ->
            CustomEventSample(
                eventName = record.get("event_name", String::class.java),
                workerId = record.get("worker_id", String::class.java),
                ts = toInstant(record.get("ts")),
                payload = toPayloadMap(record.get("payload"))
            )
        }
    }

    private fun fetchEventTypeSummaries(
        appName: String,
        selectedWorkerId: String?
    ): List<LogCatalogEventTypeSummaryResponse> {
        val conditions = mutableListOf(DSL.field("a.app_name").eq(appName))
        selectedWorkerId?.let { conditions.add(DSL.field("a.worker_id").eq(it)) }

        return dsl.select(
            DSL.field("s.event_type"),
            DSL.sum(DSL.field("s.count", Long::class.java)),
            DSL.sum(DSL.field("s.error_count", Long::class.java))
        )
            .from(DSL.table("event_stats").`as`("s"))
            .join(DSL.table("agents").`as`("a"))
            .on(DSL.field("s.agent_id").eq(DSL.field("a.id")))
            .where(conditions)
            .groupBy(DSL.field("s.event_type"))
            .orderBy(DSL.field("s.event_type").asc())
            .fetch { record ->
                LogCatalogEventTypeSummaryResponse(
                    eventType = record.get(0, String::class.java),
                    count = record.get(1, Long::class.java) ?: 0L,
                    errorCount = record.get(2, Long::class.java) ?: 0L
                )
            }
    }

    private fun fetchFailureSummaries(workerIds: List<String>): List<LogCatalogFailureSummaryResponse> {
        if (workerIds.isEmpty()) return emptyList()

        return dsl.select(
            DSL.field("reason_code"),
            DSL.sum(DSL.field("count", Long::class.java))
        )
            .from(DSL.table("ingest_failure_stats"))
            .where(DSL.field("worker_id").`in`(workerIds))
            .groupBy(DSL.field("reason_code"))
            .orderBy(DSL.field("reason_code").asc())
            .fetch { record ->
                LogCatalogFailureSummaryResponse(
                    reasonCode = record.get(0, String::class.java),
                    count = record.get(1, Long::class.java) ?: 0L
                )
            }
    }

    private fun normalizeSampleSize(sampleSize: Int?): Int =
        when {
            sampleSize == null -> 5
            sampleSize < 1 -> 5
            sampleSize > 20 -> 20
            else -> sampleSize
        }

    private fun toFieldResponse(field: Map<String, Any>): LogCatalogFieldResponse? {
        val name = field["name"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val type = runCatching {
            FieldType.valueOf(field["type"]?.toString()?.uppercase() ?: FieldType.STRING.name)
        }.getOrDefault(FieldType.STRING)
        val required = field["required"] as? Boolean ?: false
        val description = field["description"]?.toString()?.takeIf { it.isNotBlank() }
        val example = field["example"]

        return LogCatalogFieldResponse(
            name = name,
            type = type,
            required = required,
            description = description,
            example = example
        )
    }

    private fun maskPayload(payload: Map<String, Any?>): Map<String, Any?> =
        payload.mapValues { (key, value) ->
            if (shouldMask(key)) {
                "***"
            } else {
                maskValue(value)
            }
        }

    private fun maskValue(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> value.entries.associate { (key, nestedValue) ->
                val keyString = key.toString()
                keyString to if (shouldMask(keyString)) "***" else maskValue(nestedValue)
            }
            is List<*> -> value.map { maskValue(it) }
            else -> value
        }

    private fun shouldMask(key: String): Boolean {
        val normalized = key.lowercase()
        return MASK_KEYWORDS.any { normalized.contains(it) }
    }

    private fun toPayloadMap(value: Any?): Map<String, Any?> {
        if (value == null) return emptyMap()
        val json = value.toString()
        if (json.isBlank()) return emptyMap()
        return objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
    }

    private fun toInstant(value: Any?): Instant =
        when (value) {
            is Instant -> value
            is Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> Instant.parse(value.toString())
        }

    private fun fieldRequestComparator(): Comparator<com.logfriends.platform.domain.fieldrequest.entity.FieldRequest> =
        compareBy<com.logfriends.platform.domain.fieldrequest.entity.FieldRequest> {
            when (it.status) {
                FieldRequestStatus.REQUESTED -> 0
                FieldRequestStatus.ACCEPTED -> 1
                FieldRequestStatus.DONE -> 2
                FieldRequestStatus.REJECTED -> 3
            }
        }.thenByDescending { it.createdAt }

    private data class CustomEventSample(
        val eventName: String,
        val workerId: String,
        val ts: Instant,
        val payload: Map<String, Any?>
    )

    companion object {
        private val MASK_KEYWORDS = listOf(
            "password",
            "passwd",
            "token",
            "secret",
            "authorization",
            "email",
            "phone",
            "ssn",
            "resident"
        )
    }
}
