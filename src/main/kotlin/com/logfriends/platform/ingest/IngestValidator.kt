package com.logfriends.platform.ingest

import com.logfriends.platform.api.dto.EventPayload
import java.nio.charset.StandardCharsets
import java.time.Instant

class IngestValidator {

    fun validate(workerId: String, event: EventPayload): IngestFailureReason? {
        if (workerId.isBlank()) return IngestFailureReason.MISSING_WORKER_ID
        if (workerId.length > MAX_IDENTIFIER_LENGTH) return IngestFailureReason.INVALID_EVENT_ID
        if (event.type.isBlank()) return IngestFailureReason.MISSING_TYPE
        if (event.type !in SUPPORTED_TYPES) return IngestFailureReason.UNKNOWN_TYPE
        if (event.timestamp.isBlank()) return IngestFailureReason.MISSING_TIMESTAMP
        if (parseTsOrNull(event.timestamp) == null) return IngestFailureReason.INVALID_TIMESTAMP

        if (event.eventId != null && (event.eventId.isBlank() || event.eventId.length > MAX_IDENTIFIER_LENGTH)) {
            return IngestFailureReason.INVALID_EVENT_ID
        }
        if (event.sessionId != null && (event.sessionId.isBlank() || event.sessionId.length > MAX_IDENTIFIER_LENGTH)) {
            return IngestFailureReason.INVALID_SESSION_ID
        }
        if (event.appInstanceId != null && (event.appInstanceId.isBlank() || event.appInstanceId.length > MAX_IDENTIFIER_LENGTH)) {
            return IngestFailureReason.INVALID_APP_INSTANCE_ID
        }

        if (event.type == "LOG_EVENT") {
            val eventName = event.eventName
            if (eventName.isNullOrBlank()) return IngestFailureReason.MISSING_EVENT_NAME
            if (eventName.length > MAX_IDENTIFIER_LENGTH || !EVENT_NAME_REGEX.matches(eventName)) {
                return IngestFailureReason.INVALID_EVENT_NAME
            }
        }

        // Validate payload object depth
        if (event.payload != null && getObjectDepth(event.payload) > MAX_PAYLOAD_DEPTH) {
            return IngestFailureReason.PAYLOAD_TOO_DEEP
        }

        // Validate estimated event byte size
        if (estimateEventByteSize(event) > MAX_EVENT_BYTES) {
            return IngestFailureReason.EVENT_TOO_LARGE
        }

        return null
    }

    fun parseTsOrNull(ts: String): Instant? = try {
        Instant.parse(ts)
    } catch (_: Exception) {
        null
    }

    fun getObjectDepth(obj: Any?, currentDepth: Int = 1): Int {
        if (obj == null) return currentDepth
        return when (obj) {
            is Map<*, *> -> {
                if (obj.isEmpty()) currentDepth
                else obj.values.maxOfOrNull { getObjectDepth(it, currentDepth + 1) } ?: currentDepth
            }
            is Collection<*> -> {
                if (obj.isEmpty()) currentDepth
                else obj.maxOfOrNull { getObjectDepth(it, currentDepth + 1) } ?: currentDepth
            }
            is Array<*> -> {
                if (obj.isEmpty()) currentDepth
                else obj.maxOfOrNull { getObjectDepth(it, currentDepth + 1) } ?: currentDepth
            }
            else -> currentDepth
        }
    }

    fun estimateEventByteSize(event: EventPayload): Int {
        var size = 0
        size += event.type.toByteArray(StandardCharsets.UTF_8).size
        size += event.timestamp.toByteArray(StandardCharsets.UTF_8).size
        event.eventId?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.sessionId?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.appInstanceId?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.eventName?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.message?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.sql?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.uri?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.exception?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.exceptionStack?.let { size += it.toByteArray(StandardCharsets.UTF_8).size }
        event.payload?.let { size += estimateValueSize(it) }
        return size
    }

    private fun estimateValueSize(value: Any?): Int {
        if (value == null) return 0
        return when (value) {
            is String -> value.toByteArray(StandardCharsets.UTF_8).size
            is Number -> 8
            is Boolean -> 4
            is Map<*, *> -> value.entries.sumOf {
                it.key.toString().toByteArray(StandardCharsets.UTF_8).size + estimateValueSize(it.value)
            }
            is Collection<*> -> value.sumOf { estimateValueSize(it) }
            is Array<*> -> value.sumOf { estimateValueSize(it) }
            else -> value.toString().toByteArray(StandardCharsets.UTF_8).size
        }
    }

    companion object {
        const val MAX_IDENTIFIER_LENGTH = 100
        const val MAX_PAYLOAD_DEPTH = 8
        const val MAX_EVENT_BYTES = 32 * 1024 // 32KB
        const val MAX_BATCH_EVENTS = 50
        private val SUPPORTED_TYPES = setOf("HTTP", "LOG", "JDBC", "METHOD_TRACE", "LOG_EVENT")
        private val EVENT_NAME_REGEX = Regex("^[a-z][a-zA-Z0-9]*$")
    }
}
