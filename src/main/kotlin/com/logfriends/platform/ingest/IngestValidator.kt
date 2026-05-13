package com.logfriends.platform.ingest

import com.logfriends.platform.api.dto.EventPayload
import java.time.Instant

class IngestValidator {

    fun validate(workerId: String, event: EventPayload): IngestFailureReason? {
        if (workerId.isBlank()) return IngestFailureReason.MISSING_WORKER_ID
        if (event.type.isBlank()) return IngestFailureReason.MISSING_TYPE
        if (event.type !in SUPPORTED_TYPES) return IngestFailureReason.UNKNOWN_TYPE
        if (event.timestamp.isBlank()) return IngestFailureReason.MISSING_TIMESTAMP
        if (parseTsOrNull(event.timestamp) == null) return IngestFailureReason.INVALID_TIMESTAMP
        if (event.type == "LOG_EVENT" && event.eventName.isNullOrBlank()) {
            return IngestFailureReason.MISSING_EVENT_NAME
        }
        return null
    }

    fun parseTsOrNull(ts: String): Instant? = try {
        Instant.parse(ts)
    } catch (_: Exception) {
        null
    }

    private companion object {
        private val SUPPORTED_TYPES = setOf("HTTP", "LOG", "JDBC", "METHOD_TRACE", "LOG_EVENT")
    }
}
