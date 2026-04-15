package com.logfriends.platform.domain.incident.entity

import com.logfriends.platform.common.entity.BaseEntity
import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.alert.entity.Severity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "incidents")
class Incident(

    @Column(nullable = false)
    val ruleId: Long,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IncidentStatus = IncidentStatus.TRIGGERED,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val severity: Severity,

    @Column(nullable = false)
    val triggeredAt: Instant = Instant.now(),

    var acknowledgedAt: Instant? = null,
    var resolvedAt: Instant? = null,
    var acknowledgedBy: Long? = null,
    var resolvedBy: Long? = null,
    var summary: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var timeline: List<Map<String, Any>> = mutableListOf(
        mapOf("at" to Instant.now().toString(), "action" to "TRIGGERED")
    ),

    @Version
    val version: Int = 0

) : BaseEntity() {

    /**
     * 상태 머신: TRIGGERED → ACKNOWLEDGED
     */
    fun acknowledge(userId: Long) {
        assertTransition(IncidentStatus.ACKNOWLEDGED)
        this.status = IncidentStatus.ACKNOWLEDGED
        this.acknowledgedAt = Instant.now()
        this.acknowledgedBy = userId
        addTimeline("ACKNOWLEDGED", userId)
    }

    /**
     * 상태 머신: TRIGGERED or ACKNOWLEDGED → RESOLVED
     */
    fun resolve(userId: Long, summary: String?) {
        assertTransition(IncidentStatus.RESOLVED)
        this.status = IncidentStatus.RESOLVED
        this.resolvedAt = Instant.now()
        this.resolvedBy = userId
        this.summary = summary
        addTimeline("RESOLVED", userId, summary)
    }

    private fun assertTransition(target: IncidentStatus) {
        val allowed = VALID_TRANSITIONS[status]
            ?: throw BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                "현재 상태(${status})에서 전이할 수 없습니다")
        if (target !in allowed) {
            throw BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                "${status} → ${target} 전이는 허용되지 않습니다. 가능한 전이: $allowed")
        }
    }

    private fun addTimeline(action: String, userId: Long? = null, detail: String? = null) {
        val entry = mutableMapOf<String, Any>(
            "at" to Instant.now().toString(),
            "action" to action
        )
        userId?.let { entry["userId"] = it }
        detail?.let { entry["detail"] = it }

        val mutableTimeline = timeline.toMutableList()
        mutableTimeline.add(entry)
        this.timeline = mutableTimeline
    }

    companion object {
        /**
         * 유효한 상태 전이 맵
         * TRIGGERED   → ACKNOWLEDGED, RESOLVED
         * ACKNOWLEDGED → RESOLVED
         * RESOLVED    → (없음, 최종 상태)
         */
        private val VALID_TRANSITIONS = mapOf(
            IncidentStatus.TRIGGERED to setOf(IncidentStatus.ACKNOWLEDGED, IncidentStatus.RESOLVED),
            IncidentStatus.ACKNOWLEDGED to setOf(IncidentStatus.RESOLVED)
        )
    }
}

enum class IncidentStatus {
    TRIGGERED, ACKNOWLEDGED, RESOLVED
}
