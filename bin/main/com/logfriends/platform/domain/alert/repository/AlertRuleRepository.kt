package com.logfriends.platform.domain.alert.repository

import com.logfriends.platform.domain.alert.entity.AlertRule
import com.logfriends.platform.domain.alert.entity.Severity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AlertRuleRepository : JpaRepository<AlertRule, Long> {

    fun findByEnabledTrue(): List<AlertRule>

    fun findBySeverity(severity: Severity): List<AlertRule>

    @Query("SELECT a FROM AlertRule a WHERE a.enabled = true AND a.severity = :severity")
    fun findActiveRulesBySeverity(severity: Severity): List<AlertRule>
}
