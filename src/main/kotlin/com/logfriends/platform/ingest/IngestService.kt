package com.logfriends.platform.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.logfriends.platform.api.dto.EventPayload
import com.logfriends.platform.api.dto.IngestRequest
import com.logfriends.platform.api.dto.IngestResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IngestService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
    private val ingestValidator: IngestValidator = IngestValidator()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun save(request: IngestRequest): IngestResponse {
        if (request.events.isEmpty()) {
            return IngestResponse(received = 0, stored = 0, failed = 0)
        }

        var stored = 0
        var failed = 0

        request.events.forEach { event ->
            val validation = ingestValidator.validate(request.workerId, event)
            if (validation != null) {
                failed++
                saveFailedEvent(request.workerId, event, validation)
                return@forEach
            }

            try {
                when (event.type) {
                "LOG" -> {
                    saveLogEvents(request.workerId, listOf(event))
                    stored++
                }
                "HTTP" -> {
                    saveHttpEvents(request.workerId, listOf(event))
                    stored++
                }
                "JDBC" -> {
                    saveJdbcEvents(request.workerId, listOf(event))
                    stored++
                }
                "METHOD_TRACE" -> {
                    saveMethodTraceEvents(request.workerId, listOf(event))
                    stored++
                }
                "LOG_EVENT" -> {
                    saveCustomEvents(request.workerId, listOf(event))
                    stored++
                }
                else -> {
                    failed++
                    saveFailedEvent(request.workerId, event, IngestFailureReason.UNKNOWN_TYPE)
                }
            }
            } catch (ex: Exception) {
                failed++
                log.error(
                    "[Ingest] raw event store failed worker={} type={}",
                    request.workerId,
                    event.type,
                    ex
                )
                saveFailedEvent(request.workerId, event, IngestFailureReason.STORE_FAILED)
            }
        }

        log.debug(
            "[Ingest] worker={} received={} stored={} failed={}",
            request.workerId,
            request.events.size,
            stored,
            failed
        )

        return IngestResponse(
            received = request.events.size,
            stored = stored,
            failed = failed
        )
    }

    private fun saveLogEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
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
    }

    private fun saveHttpEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
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
    }

    private fun saveJdbcEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
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
    }

    private fun saveMethodTraceEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
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
    }

    private fun saveCustomEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("custom_events"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("event_name"), DSL.field("payload")
                )
                .values(
                    workerId, ingestValidator.parseTsOrNull(e.timestamp)!!,
                    e.eventName ?: "unknown", toJsonb(e.payload)
                )
        }).execute()
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
