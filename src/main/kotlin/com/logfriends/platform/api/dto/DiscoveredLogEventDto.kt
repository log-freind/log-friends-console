package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEvent
import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEventStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class DiscoveredLogEventReportRequest(
    @field:NotBlank(message = "workerId는 필수입니다")
    val workerId: String,
    @field:NotBlank(message = "appName은 필수입니다")
    val appName: String,
    val appVersion: String? = null,
    @field:Valid
    val events: List<DiscoveredLogEventItemRequest> = emptyList()
)

data class DiscoveredLogEventItemRequest(
    val eventName: String,
    val sourceClass: String,
    val sourceMethod: String,
    val parameterNames: List<String> = emptyList()
)

data class DiscoveredLogEventReportResponse(
    val received: Int,
    val upserted: Int
)

data class DiscoveredLogEventResponse(
    val id: Long,
    val agentId: Long,
    val eventName: String,
    val sourceClass: String,
    val sourceMethod: String,
    val parameterNames: List<String>,
    val appVersion: String?,
    val status: DiscoveredLogEventStatus,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(discoveredLogEvent: DiscoveredLogEvent) = DiscoveredLogEventResponse(
            id = discoveredLogEvent.id!!,
            agentId = discoveredLogEvent.agent.id!!,
            eventName = discoveredLogEvent.eventName,
            sourceClass = discoveredLogEvent.sourceClass,
            sourceMethod = discoveredLogEvent.sourceMethod,
            parameterNames = discoveredLogEvent.parameterNames,
            appVersion = discoveredLogEvent.appVersion,
            status = discoveredLogEvent.status,
            firstSeenAt = discoveredLogEvent.firstSeenAt,
            lastSeenAt = discoveredLogEvent.lastSeenAt,
            createdAt = discoveredLogEvent.createdAt,
            updatedAt = discoveredLogEvent.updatedAt
        )
    }
}
