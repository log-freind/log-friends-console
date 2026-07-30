package com.logfriends.platform.overview.traffic

data class TrafficOverviewResponse(
    val items: List<TrafficOverviewItem>
)

data class TrafficOverviewItem(
    val method: String,
    val uri: String,
    val requestCount: Long
)
