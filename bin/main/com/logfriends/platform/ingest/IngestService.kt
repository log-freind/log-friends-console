package com.logfriends.platform.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class IngestService(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun save(request: IngestRequest) {
        if (request.events.isEmpty()) return

        val grouped = request.events.groupBy { it.type }

        grouped["LOG"]?.let          { saveLogEvents(request.workerId, it) }
        grouped["HTTP"]?.let         { saveHttpEvents(request.workerId, it) }
        grouped["JDBC"]?.let         { saveJdbcEvents(request.workerId, it) }
        grouped["METHOD_TRACE"]?.let { saveMethodTraceEvents(request.workerId, it) }
        grouped["LOG_EVENT"]?.let    { saveCustomEvents(request.workerId, it) }

        log.debug("[Ingest] worker={} events={}", request.workerId, request.events.size)
    }

    private fun saveLogEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("log_events"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("level"), DSL.field("logger_name"),
                    DSL.field("thread_name"), DSL.field("message"),
                    DSL.field("exception"), DSL.field("exception_stack"),
                    DSL.field("trace_id"), DSL.field("mdc")
                )
                .values(
                    workerId, parseTs(e.timestamp),
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
                    workerId, parseTs(e.timestamp),
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
                    workerId, parseTs(e.timestamp),
                    e.sql ?: "", e.durationMs ?: 0L,
                    e.rowCount ?: 0, e.traceId ?: "",
                    e.exception ?: "", e.exceptionStack ?: ""
                )
        }).execute()
    }

    private fun saveMethodTraceEvents(workerId: String, events: List<EventPayload>) {
        dsl.batch(events.map { e ->
            dsl.insertInto(DSL.table("method_trace_events"))
                .columns(
                    DSL.field("worker_id"), DSL.field("ts"),
                    DSL.field("class_name"), DSL.field("method_name"),
                    DSL.field("duration_ms"), DSL.field("trace_id"),
                    DSL.field("exception"), DSL.field("exception_stack")
                )
                .values(
                    workerId, parseTs(e.timestamp),
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
                    DSL.field("event_name"), DSL.field("fields")
                )
                .values(
                    workerId, parseTs(e.timestamp),
                    e.eventName ?: "unknown", toJsonb(e.fields)
                )
        }).execute()
    }

    private fun parseTs(ts: String): Any = try {
        Instant.parse(ts)
    } catch (_: Exception) {
        Instant.now()
    }

    private fun toJsonb(map: Map<String, String>?): String =
        if (map.isNullOrEmpty()) "{}" else objectMapper.writeValueAsString(map)
}
