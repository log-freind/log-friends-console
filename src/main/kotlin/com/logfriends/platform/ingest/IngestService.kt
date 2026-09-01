package com.logfriends.platform.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.logfriends.platform.api.dto.EventPayload
import com.logfriends.platform.api.dto.IngestRequest
import com.logfriends.platform.api.dto.IngestResponse
import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IngestService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val ingestBatchProperties: IngestBatchProperties,
    private val ingestValidator: IngestValidator = IngestValidator(),
    private val rateLimiter: IngestRateLimiter = IngestRateLimiter()
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val eventPartitioner = IngestEventPartitioner(ingestValidator)

    fun save(request: IngestRequest, clientIp: String? = null): IngestResponse {
        if (request.events.isEmpty()) {
            return IngestResponse(received = 0, stored = 0, failed = 0)
        }

        // 1. Validate max batch size
        if (request.events.size > IngestValidator.MAX_BATCH_EVENTS) {
            throw BusinessException(ErrorCode.BATCH_TOO_LARGE)
        }

        // 2. Instance-local rate limiting
        val rateLimitKey = if (!clientIp.isNullOrBlank()) "$clientIp:${request.workerId}" else request.workerId
        if (!rateLimiter.tryAcquire(rateLimitKey)) {
            log.warn("[Ingest] rate limit exceeded for key={}", rateLimitKey)
            throw BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED)
        }

        var stored = 0
        var failed = 0

        val partition = eventPartitioner.partition(request.workerId, request.events)
        partition.invalidEvents.forEach { invalid ->
            failed++
            saveFailedEvent(request.workerId, invalid.event, invalid.reason)
        }

        partition.validEventsByType.forEach { (type, events) ->
            events.chunked(ingestBatchProperties.dbBatchSize).forEach { batch ->
                try {
                    val batchStored = when (type) {
                        "LOG" -> saveLogEvents(request.workerId, batch)
                        "HTTP" -> saveHttpEvents(request.workerId, batch)
                        "JDBC" -> saveJdbcEvents(request.workerId, batch)
                        "METHOD_TRACE" -> saveMethodTraceEvents(request.workerId, batch)
                        "LOG_EVENT" -> saveCustomEvents(request.workerId, batch)
                        else -> error("validated event type must be supported: $type")
                    }
                    stored += batchStored
                } catch (ex: Exception) {
                    failed += batch.size
                    log.error(
                        "[Ingest] raw event store failed worker={} type={}",
                        request.workerId,
                        type,
                        ex
                    )
                    batch.forEach { event ->
                        saveFailedEvent(request.workerId, event, IngestFailureReason.STORE_FAILED)
                    }
                }
            }
        }

        val duplicates = request.events.size - stored - failed

        log.debug(
            "[Ingest] worker={} received={} stored={} duplicates={} failed={}",
            request.workerId,
            request.events.size,
            stored,
            duplicates,
            failed
        )

        return IngestResponse(
            received = request.events.size,
            stored = stored,
            failed = failed
        )
    }

    private fun saveLogEvents(workerId: String, events: List<EventPayload>): Int {
        val result = dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("logs"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("level"), DSL.field("logger_name"),
                    DSL.field("thread_name"), DSL.field("message"),
                    DSL.field("exception"), DSL.field("exception_stack"),
                    DSL.field("trace_id"), DSL.field("mdc")
                )
                .values(
                    workerId, ingestValidator.parseTsOrNull(e.timestamp)!!,
                    e.level ?: "INFO", e.loggerName ?: "",
                    e.threadName ?: "", e.message ?: "",
                    e.exception ?: "", e.exceptionStack ?: "",
                    e.traceId ?: "", toJsonb(e.mdc)
                )
        }).execute()
        return result.count { it > 0 || it == -2 }.coerceAtMost(events.size).let { if (it == 0 && result.isNotEmpty()) events.size else it }
    }

    private fun saveHttpEvents(workerId: String, events: List<EventPayload>): Int {
        val result = dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("http_events"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("method"), DSL.field("uri"),
                    DSL.field("status_code"), DSL.field("duration_ms"),
                    DSL.field("trace_id"), DSL.field("exception_stack"),
                    DSL.field("request_headers")
                )
                .values(
                    workerId, ingestValidator.parseTsOrNull(e.timestamp)!!,
                    e.method ?: "", e.uri ?: "",
                    e.statusCode ?: 0, e.durationMs ?: 0L,
                    e.traceId ?: "", e.exceptionStack ?: "",
                    toJsonb(e.requestHeaders)
                )
        }).execute()
        return result.count { it > 0 || it == -2 }.coerceAtMost(events.size).let { if (it == 0 && result.isNotEmpty()) events.size else it }
    }

    private fun saveJdbcEvents(workerId: String, events: List<EventPayload>): Int {
        val result = dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("jdbc_events"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("sql_text"), DSL.field("duration_ms"),
                    DSL.field("row_count"), DSL.field("trace_id"),
                    DSL.field("exception"), DSL.field("exception_stack")
                )
                .values(
                    workerId, ingestValidator.parseTsOrNull(e.timestamp)!!,
                    e.sql ?: "", e.durationMs ?: 0L,
                    e.rowCount ?: 0, e.traceId ?: "",
                    e.exception ?: "", e.exceptionStack ?: ""
                )
        }).execute()
        return result.count { it > 0 || it == -2 }.coerceAtMost(events.size).let { if (it == 0 && result.isNotEmpty()) events.size else it }
    }

    private fun saveMethodTraceEvents(workerId: String, events: List<EventPayload>): Int {
        val result = dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("method_traces"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("class_name"), DSL.field("method_name"),
                    DSL.field("duration_ms"), DSL.field("trace_id"),
                    DSL.field("exception"), DSL.field("exception_stack")
                )
                .values(
                    workerId, ingestValidator.parseTsOrNull(e.timestamp)!!,
                    e.className ?: "", e.methodName ?: "",
                    e.durationMs ?: 0L, e.traceId ?: "",
                    e.exception ?: "", e.exceptionStack ?: ""
                )
        }).execute()
        return result.count { it > 0 || it == -2 }.coerceAtMost(events.size).let { if (it == 0 && result.isNotEmpty()) events.size else it }
    }

    private fun saveCustomEvents(workerId: String, events: List<EventPayload>): Int {
        val now = java.time.Instant.now()
        val deduplicated = mutableListOf<EventPayload>()
        val seenEventKeys = mutableSetOf<Pair<String, String>>()
        for (e in events) {
            val eventId = e.eventId
            if (eventId != null) {
                val key = Pair(eventId, e.timestamp)
                if (seenEventKeys.add(key)) {
                    deduplicated.add(e)
                }
            } else {
                deduplicated.add(e)
            }
        }

        if (deduplicated.isEmpty()) {
            return 0
        }

        val result = dsl.batch(deduplicated.map { e ->
            dsl.insertInto(DSL.table("custom_events"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("event_name"), DSL.field("payload"),
                    DSL.field("event_id"), DSL.field("session_id"),
                    DSL.field("app_instance_id"), DSL.field("received_at"),
                    DSL.field("page_path"), DSL.field("component_name"),
                    DSL.field("parent_component_name"), DSL.field("component_path")
                )
                .values(
                    workerId, ingestValidator.parseTsOrNull(e.timestamp)!!,
                    e.eventName ?: "unknown", toJsonb(e.payload),
                    e.eventId, e.sessionId, e.appInstanceId, now,
                    e.uiContext?.page, e.uiContext?.component, e.uiContext?.parentComponent,
                    toJsonb(e.uiContext?.componentPath?.let { mapOf("items" to it) })
                )
                .onDuplicateKeyIgnore()
        }).execute()

        // Count statements that actually affected > 0 rows (SUCCESS_NO_INFO is -2 in JDBC)
        val inserted = result.count { it > 0 }
        return if (inserted == 0 && result.all { it == -2 }) {
            deduplicated.size
        } else {
            inserted
        }
    }

    private fun saveFailedEvent(workerId: String, event: EventPayload, reason: IngestFailureReason) {
        try {
            dsl.insertInto(DSL.table("ingest_failed_events"))
                .columns(
                    DSL.field("worker_id"),
                    DSL.field("event_type"),
                    DSL.field("reason_code"),
                    DSL.field("reason"),
                    DSL.field("payload")
                )
                .values(
                    workerId.ifBlank { null },
                    event.type.ifBlank { null },
                    reason.name,
                    reason.message,
                    toJsonb(toPayloadJson(event))
                )
                .execute()

            log.warn(
                "[Ingest] failed worker={} reason={} type={}",
                workerId,
                reason.name,
                event.type
            )
        } catch (ex: Exception) {
            log.error(
                "[Ingest] failed event store failed worker={} reason={} type={}",
                workerId,
                reason.name,
                event.type,
                ex
            )
        }
    }

    private fun toPayloadJson(event: EventPayload): Map<String, Any?> {
        val node = objectMapper.valueToTree<ObjectNode>(event)
        return objectMapper.convertValue(node, Map::class.java) as Map<String, Any?>
    }

    private fun toJsonb(map: Map<*, *>?): String =
        if (map.isNullOrEmpty()) "{}" else objectMapper.writeValueAsString(map)
}
