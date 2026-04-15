package com.logfriends.platform.domain.alert.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.alert.entity.AlertRule
import com.logfriends.platform.domain.alert.entity.Severity
import com.logfriends.platform.domain.alert.repository.AlertRuleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AlertRuleService(
    private val alertRuleRepository: AlertRuleRepository
) {

    fun findAll(): List<AlertRule> = alertRuleRepository.findAll()

    fun findById(id: Long): AlertRule =
        alertRuleRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.ALERT_RULE_NOT_FOUND) }

    fun findActiveRules(): List<AlertRule> = alertRuleRepository.findByEnabledTrue()

    @Transactional
    fun create(
        name: String,
        description: String?,
        severity: Severity,
        condition: Map<String, Any>,
        notifyChannels: List<String>,
        cooldownMinutes: Int
    ): AlertRule {
        validateCondition(condition)

        val rule = AlertRule(
            name = name,
            description = description,
            severity = severity,
            condition = condition,
            notifyChannels = notifyChannels.toTypedArray(),
            cooldownMinutes = cooldownMinutes
        )
        return alertRuleRepository.save(rule)
    }

    @Transactional
    fun update(
        id: Long,
        name: String?,
        description: String?,
        severity: Severity?,
        condition: Map<String, Any>?,
        notifyChannels: List<String>?,
        cooldownMinutes: Int?
    ): AlertRule {
        val rule = findById(id)

        name?.let { rule.name = it }
        description?.let { rule.description = it }
        severity?.let { rule.severity = it }
        condition?.let {
            validateCondition(it)
            rule.condition = it
        }
        notifyChannels?.let { rule.notifyChannels = it.toTypedArray() }
        cooldownMinutes?.let { rule.cooldownMinutes = it }

        return alertRuleRepository.save(rule)
    }

    @Transactional
    fun toggleEnabled(id: Long): AlertRule {
        val rule = findById(id)
        if (rule.enabled) rule.disable() else rule.enable()
        return alertRuleRepository.save(rule)
    }

    @Transactional
    fun delete(id: Long) {
        val rule = findById(id)
        alertRuleRepository.delete(rule)
    }

    private fun validateCondition(condition: Map<String, Any>) {
        if (!condition.containsKey("type")) {
            throw BusinessException(ErrorCode.INVALID_ALERT_CONDITION,
                "조건에 'type' 필드가 필요합니다")
        }
        val type = condition["type"] as? String
            ?: throw BusinessException(ErrorCode.INVALID_ALERT_CONDITION,
                "'type'은 문자열이어야 합니다")

        val validTypes = setOf("LOG_COUNT", "LATENCY", "ERROR_RATE", "CUSTOM")
        if (type !in validTypes) {
            throw BusinessException(ErrorCode.INVALID_ALERT_CONDITION,
                "유효한 type: $validTypes")
        }
    }
}
