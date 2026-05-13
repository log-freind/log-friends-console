package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.eventstat.entity.EventStat
import com.logfriends.platform.domain.eventstat.entity.EventType
import com.logfriends.platform.domain.eventstat.service.EventStatRequest
import com.logfriends.platform.domain.eventstat.service.EventTypeSummary
import java.time.Instant

// === Request ===

data class EventStatRecordRequest(
    val stats: List<EventStatItemRequest>
)

data class EventStatItemRequest(
    val eventType: EventType,
    val windowStart: Instant,
    val count: Long,
    val errorCount: Long = 0,
    val avgDurationMs: Double? = null
) {
    fun toServiceRequest() = EventStatRequest(eventType, windowStart, count, errorCount, avgDurationMs)
}

// === Response ===

data class EventStatResponse(
    val id: Long,
    val agentId: Long,
    val eventType: EventType,
    val windowStart: Instant,
    val count: Long,
    val errorCount: Long,
    val avgDurationMs: Double?
) {
    companion object {
        fun from(s: EventStat) = EventStatResponse(
            id = s.id!!,
            agentId = s.agentId,
            eventType = s.eventType,
            windowStart = s.windowStart,
            count = s.count,
            errorCount = s.errorCount,
            avgDurationMs = s.avgDurationMs
        )
    }
}

data class EventTypeSummaryResponse(
    val eventType: EventType,
    val totalCount: Long,
    val totalErrorCount: Long,
    val errorRate: Double
) {
    companion object {
        fun from(s: EventTypeSummary) = EventTypeSummaryResponse(
            eventType = s.eventType,
            totalCount = s.totalCount,
            totalErrorCount = s.totalErrorCount,
            errorRate = if (s.totalCount > 0) s.totalErrorCount.toDouble() / s.totalCount else 0.0
        )
    }
}
