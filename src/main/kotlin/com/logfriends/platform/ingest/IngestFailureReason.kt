package com.logfriends.platform.ingest

enum class IngestFailureReason(val message: String) {
    MISSING_WORKER_ID("workerId is blank"),
    MISSING_TYPE("event type is blank"),
    UNKNOWN_TYPE("event type is not supported"),
    MISSING_TIMESTAMP("timestamp is blank"),
    INVALID_TIMESTAMP("timestamp is not ISO-8601 instant"),
    MISSING_EVENT_NAME("LOG_EVENT eventName is blank"),
    INVALID_EVENT_NAME("LOG_EVENT eventName must be camelCase"),
    INVALID_EVENT_ID("eventId exceeds maximum length of 100"),
    INVALID_SESSION_ID("sessionId exceeds maximum length of 100"),
    INVALID_APP_INSTANCE_ID("appInstanceId exceeds maximum length of 100"),
    EVENT_TOO_LARGE("event UTF-8 byte size exceeds maximum 32KB"),
    PAYLOAD_TOO_DEEP("payload object depth exceeds maximum 8 levels"),
    BATCH_TOO_LARGE("batch size exceeds maximum 50 events"),
    STORE_FAILED("raw event store failed")
}
