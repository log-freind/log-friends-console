package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.alert.entity.Severity
import com.logfriends.platform.domain.incident.entity.Incident
import com.logfriends.platform.domain.incident.entity.IncidentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

// === Request ===
data class IncidentTriggerRequest(
    @field:NotNull(message = "ruleId는 필수입니다")
    val ruleId: Long,
    @field:NotBlank(message = "title은 필수입니다")
    val title: String
)

data class IncidentAckRequest(
    @field:NotNull(message = "userId는 필수입니다")
    val userId: Long
)

data class IncidentResolveRequest(
    @field:NotNull(message = "userId는 필수입니다")
    val userId: Long,
    val summary: String? = null
)

// === Response ===
data class IncidentResponse(
    val id: Long,
    val ruleId: Long,
    val title: String,
    val status: IncidentStatus,
    val severity: Severity,
    val triggeredAt: Instant,
    val acknowledgedAt: Instant?,
    val resolvedAt: Instant?,
    val acknowledgedBy: Long?,
    val resolvedBy: Long?,
    val summary: String?,
    val timeline: List<Map<String, Any>>,
    val version: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(incident: Incident) = IncidentResponse(
            id = incident.id!!,
            ruleId = incident.ruleId,
            title = incident.title,
            status = incident.status,
            severity = incident.severity,
            triggeredAt = incident.triggeredAt,
            acknowledgedAt = incident.acknowledgedAt,
            resolvedAt = incident.resolvedAt,
            acknowledgedBy = incident.acknowledgedBy,
            resolvedBy = incident.resolvedBy,
            summary = incident.summary,
            timeline = incident.timeline,
            version = incident.version,
            createdAt = incident.createdAt
        )
    }
}

data class IncidentCountResponse(
    val triggered: Long,
    val acknowledged: Long,
    val resolved: Long
)
