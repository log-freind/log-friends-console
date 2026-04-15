package com.logfriends.platform.domain.incident.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.alert.entity.Severity
import com.logfriends.platform.domain.alert.service.AlertRuleService
import com.logfriends.platform.domain.incident.entity.Incident
import com.logfriends.platform.domain.incident.entity.IncidentStatus
import com.logfriends.platform.domain.incident.repository.IncidentRepository
import com.logfriends.platform.domain.incident.repository.IncidentSpec
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val alertRuleService: AlertRuleService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findById(id: Long): Incident =
        incidentRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.INCIDENT_NOT_FOUND) }

    /**
     * Specification 기반 동적 검색 + 페이징
     */
    fun search(
        status: IncidentStatus? = null,
        severity: Severity? = null,
        ruleId: Long? = null,
        from: Instant? = null,
        to: Instant? = null,
        pageable: Pageable
    ): Page<Incident> {
        val spec = IncidentSpec.search(status, severity, ruleId, from, to)
        return incidentRepository.findAll(spec, pageable)
    }

    /**
     * 알림 규칙 발화 → 새 인시던트 생성
     */
    @Transactional
    fun trigger(ruleId: Long, title: String): Incident {
        val rule = alertRuleService.findById(ruleId)

        if (!rule.canTrigger()) {
            log.info("Rule {} is in cooldown, skipping", ruleId)
            throw BusinessException(ErrorCode.CONFLICT,
                "알림 규칙이 쿨다운 중입니다 (${rule.cooldownMinutes}분)")
        }

        rule.markTriggered()

        val incident = Incident(
            ruleId = ruleId,
            title = title,
            severity = rule.severity
        )

        log.info("[Platform] Incident triggered: rule={}, title={}", ruleId, title)
        return incidentRepository.save(incident)
    }

    /**
     * 인시던트 인지 (TRIGGERED → ACKNOWLEDGED)
     * @throws BusinessException 잘못된 상태 전이
     * @throws BusinessException Optimistic Lock 충돌
     */
    @Transactional
    fun acknowledge(id: Long, userId: Long): Incident {
        return updateWithOptimisticLock(id) { incident ->
            incident.acknowledge(userId)
        }
    }

    /**
     * 인시던트 해결 (→ RESOLVED)
     */
    @Transactional
    fun resolve(id: Long, userId: Long, summary: String?): Incident {
        return updateWithOptimisticLock(id) { incident ->
            incident.resolve(userId, summary)
        }
    }

    fun countByStatus(): Map<IncidentStatus, Long> {
        return IncidentStatus.entries.associateWith { status ->
            incidentRepository.countByStatus(status)
        }
    }

    /**
     * Optimistic Locking 보호 — @Version 충돌 시 의미 있는 에러 반환
     */
    private fun updateWithOptimisticLock(id: Long, action: (Incident) -> Unit): Incident {
        val incident = findById(id)
        action(incident)
        return try {
            incidentRepository.save(incident)
        } catch (e: ObjectOptimisticLockingFailureException) {
            throw BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT)
        }
    }
}
