package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.alert.entity.AlertRule
import com.logfriends.platform.domain.alert.entity.Severity
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

// === Request ===
data class AlertRuleCreateRequest(
    @field:NotBlank(message = "name은 필수입니다")
    val name: String,
    val description: String? = null,
    val severity: Severity = Severity.WARNING,
    @field:NotNull(message = "condition은 필수입니다")
    val condition: Map<String, Any>,
    val notifyChannels: List<String> = emptyList(),
    val cooldownMinutes: Int = 30
)

data class AlertRuleUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val severity: Severity? = null,
    val condition: Map<String, Any>? = null,
    val notifyChannels: List<String>? = null,
    val cooldownMinutes: Int? = null
)

// === Response ===
data class AlertRuleResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val severity: Severity,
    val condition: Map<String, Any>,
    val notifyChannels: List<String>,
    val cooldownMinutes: Int,
    val enabled: Boolean,
    val lastTriggered: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(rule: AlertRule) = AlertRuleResponse(
            id = rule.id!!,
            name = rule.name,
            description = rule.description,
            severity = rule.severity,
            condition = rule.condition,
            notifyChannels = rule.notifyChannels.toList(),
            cooldownMinutes = rule.cooldownMinutes,
            enabled = rule.enabled,
            lastTriggered = rule.lastTriggered,
            createdAt = rule.createdAt,
            updatedAt = rule.updatedAt
        )
    }
}
