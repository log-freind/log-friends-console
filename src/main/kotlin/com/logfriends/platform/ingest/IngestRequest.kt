package com.logfriends.platform.ingest

data class IngestRequest(
    val workerId: String,
    val events: List<EventPayload>
)

data class EventPayload(
    val type: String,
    val timestamp: String,
    // HTTP
    val method: String? = null,
    val uri: String? = null,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val traceId: String? = null,
    val exceptionStack: String? = null,
    val requestHeaders: Map<String, String>? = null,
    // LOG
    val level: String? = null,
    val loggerName: String? = null,
    val threadName: String? = null,
    val message: String? = null,
    val exception: String? = null,
    val mdc: Map<String, String>? = null,
    // JDBC
    val sql: String? = null,
    val rowCount: Int? = null,
    // METHOD_TRACE
    val className: String? = null,
    val methodName: String? = null,
    // LOG_EVENT
    val eventName: String? = null,
    val fields: Map<String, String>? = null,
)
