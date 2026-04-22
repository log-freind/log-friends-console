package com.logfriends.platform.domain.eventstat.entity

import com.logfriends.platform.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.Instant

/**
 * Agent별 이벤트 타입 통계
 * Spark 파이프라인이나 SDK heartbeat에서 집계해서 전송
 * 1분 윈도우 단위로 저장
 */
@Entity
@Table(
    name = "event_stats",
    indexes = [
        Index(name = "idx_event_stats_agent_window", columnList = "agent_id, window_start"),
        Index(name = "idx_event_stats_event_type", columnList = "event_type")
    ]
)
class EventStat(

    @Column(name = "agent_id", nullable = false)
    val agentId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: EventType,

    @Column(name = "window_start", nullable = false)
    val windowStart: Instant,

    @Column(nullable = false)
    var count: Long = 0,

    @Column(name = "error_count", nullable = false)
    var errorCount: Long = 0,

    /** HTTP/JDBC/METHOD_TRACE 평균 응답시간(ms), LOG/LOG_EVENT는 null */
    var avgDurationMs: Double? = null

) : BaseEntity()

enum class EventType {
    HTTP, LOG, JDBC, METHOD_TRACE, LOG_EVENT
}
