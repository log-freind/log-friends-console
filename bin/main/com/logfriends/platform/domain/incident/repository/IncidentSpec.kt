package com.logfriends.platform.domain.incident.repository

import com.logfriends.platform.domain.alert.entity.Severity
import com.logfriends.platform.domain.incident.entity.Incident
import com.logfriends.platform.domain.incident.entity.IncidentStatus
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

/**
 * JPA Specification 동적 쿼리 — QueryDSL 대체
 *
 * 사용 예:
 *   val spec = IncidentSpec.hasStatus(TRIGGERED)
 *       .and(IncidentSpec.hasSeverity(CRITICAL))
 *       .and(IncidentSpec.triggeredAfter(yesterday))
 *   incidentRepository.findAll(spec, pageable)
 */
object IncidentSpec {

    fun hasStatus(status: IncidentStatus): Specification<Incident> =
        Specification { root, _, cb ->
            cb.equal(root.get<IncidentStatus>("status"), status)
        }

    fun hasSeverity(severity: Severity): Specification<Incident> =
        Specification { root, _, cb ->
            cb.equal(root.get<Severity>("severity"), severity)
        }

    fun forRule(ruleId: Long): Specification<Incident> =
        Specification { root, _, cb ->
            cb.equal(root.get<Long>("ruleId"), ruleId)
        }

    fun triggeredAfter(after: Instant): Specification<Incident> =
        Specification { root, _, cb ->
            cb.greaterThanOrEqualTo(root.get("triggeredAt"), after)
        }

    fun triggeredBefore(before: Instant): Specification<Incident> =
        Specification { root, _, cb ->
            cb.lessThanOrEqualTo(root.get("triggeredAt"), before)
        }

    /**
     * 동적 검색 조합 빌더
     * null 파라미터는 무시됨 → 조건 자동 생략
     */
    fun search(
        status: IncidentStatus? = null,
        severity: Severity? = null,
        ruleId: Long? = null,
        from: Instant? = null,
        to: Instant? = null
    ): Specification<Incident> {
        var spec = Specification.where<Incident>(null)
        status?.let { spec = spec.and(hasStatus(it)) }
        severity?.let { spec = spec.and(hasSeverity(it)) }
        ruleId?.let { spec = spec.and(forRule(it)) }
        from?.let { spec = spec.and(triggeredAfter(it)) }
        to?.let { spec = spec.and(triggeredBefore(it)) }
        return spec
    }
}
