package com.logfriends.platform.overview.performance

data class PerformanceOverviewResponse(
    val items: List<PerformanceOverviewItem>
)

data class PerformanceOverviewItem(
    val method: String,
    val uri: String,
    val requestCount: Long,
    val averageDurationMs: Double,
    val p95DurationMs: Double,
    val maxDurationMs: Long
)
