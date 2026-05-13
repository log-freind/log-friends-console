package com.logfriends.platform.ingest

enum class IngestFailureReason(val message: String) {
    MISSING_WORKER_ID("workerId is blank"),
    MISSING_TYPE("event type is blank"),
    UNKNOWN_TYPE("event type is not supported"),
    MISSING_TIMESTAMP("timestamp is blank"),
    INVALID_TIMESTAMP("timestamp is not ISO-8601 instant"),
    MISSING_EVENT_NAME("LOG_EVENT eventName is blank"),
    STORE_FAILED("raw event store failed")
}
