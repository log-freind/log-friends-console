package com.logfriends.platform.domain.incident.repository

import com.logfriends.platform.domain.alert.entity.Severity
import com.logfriends.platform.domain.incident.entity.Incident
import com.logfriends.platform.domain.incident.entity.IncidentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface IncidentRepository : JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    fun findByStatus(status: IncidentStatus): List<Incident>

    fun findByRuleId(ruleId: Long): List<Incident>

    fun findBySeverityAndStatus(severity: Severity, status: IncidentStatus): List<Incident>

    fun countByStatus(status: IncidentStatus): Long
}
