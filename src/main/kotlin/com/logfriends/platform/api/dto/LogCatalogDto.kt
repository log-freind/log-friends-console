package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.fieldrequest.entity.FieldType
import java.time.Instant

data class LogCatalogAppsResponse(
    val apps: List<LogCatalogAppResponse>
)

data class LogCatalogAppResponse(
    val appName: String,
    val workerIds: List<String>
)

data class LogCatalogEventsResponse(
    val appName: String,
    val selectedWorkerId: String?,
    val workerIds: List<String>,
    val eventTypeSummaries: List<LogCatalogEventTypeSummaryResponse>,
    val failureSummaries: List<LogCatalogFailureSummaryResponse>,
    val events: List<LogCatalogEventResponse>
)

data class LogCatalogEventTypeSummaryResponse(
    val eventType: String,
    val count: Long,
    val errorCount: Long
)

data class LogCatalogFailureSummaryResponse(
    val reasonCode: String,
    val count: Long
)

data class LogCatalogEventResponse(
    val eventName: String,
    val description: String?,
    val apiContext: LogCatalogApiContextResponse?,
    val specStatus: LogCatalogSpecStatus,
    val fields: List<LogCatalogFieldResponse>,
    val samples: List<LogCatalogSampleResponse>,
    val mismatches: List<LogCatalogMismatchResponse>,
    val fieldRequests: List<FieldRequestResponse>
)

data class LogCatalogApiContextResponse(
    val method: String?,
    val path: String?,
    val description: String?
)

data class LogCatalogFieldResponse(
    val name: String,
    val type: FieldType,
    val required: Boolean,
    val description: String? = null,
    val example: Any? = null
)

data class LogCatalogSampleResponse(
    val workerId: String,
    val ts: Instant,
    val payload: Map<String, Any?>
)

data class LogCatalogMismatchResponse(
    val code: LogCatalogMismatchCode,
    val fieldName: String
)

enum class LogCatalogSpecStatus {
    REGISTERED,
    NO_SAMPLE,
    NO_SPEC
}

enum class LogCatalogMismatchCode {
    EXTRA_FIELD,
    MISSING_FIELD
}
