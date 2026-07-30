package com.logfriends.platform.overview.reliability

data class ReliabilityOverviewResponse(
    val http: HttpReliabilitySummary,
    val ingest: IngestReliabilitySummary,
    val topHttpErrors: List<TopHttpError>,
    val topIngestFailures: List<TopIngestFailure>
)

data class HttpReliabilitySummary(
    val totalRequests: Long,
    val errorRequests: Long,
    val errorRate: Double
)

data class IngestReliabilitySummary(
    val failedEvents: Long
)

data class TopHttpError(
    val method: String,
    val uri: String,
    val statusCode: Int,
    val errorCount: Long
)

data class TopIngestFailure(
    val reasonCode: String,
    val failureCount: Long
)
