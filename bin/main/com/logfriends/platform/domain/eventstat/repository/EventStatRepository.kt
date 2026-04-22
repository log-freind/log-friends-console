package com.logfriends.platform.domain.eventstat.repository

import com.logfriends.platform.domain.eventstat.entity.EventStat
import com.logfriends.platform.domain.eventstat.entity.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EventStatRepository : JpaRepository<EventStat, Long> {

    fun findAllByAgentId(agentId: Long): List<EventStat>

    fun findAllByAgentIdAndWindowStartBetween(
        agentId: Long, from: Instant, to: Instant
    ): List<EventStat>

    /** Agent별 이벤트 타입 총합 (전체 기간) */
    @Query("""
        SELECT s.eventType, SUM(s.count), SUM(s.errorCount)
        FROM EventStat s
        WHERE s.agentId = :agentId
        GROUP BY s.eventType
    """)
    fun sumByAgentIdGroupByType(agentId: Long): List<Array<Any>>

    /** appName 기준 전체 통계 */
    @Query("""
        SELECT s.eventType, SUM(s.count), SUM(s.errorCount)
        FROM EventStat s
        WHERE s.agentId IN (
            SELECT a.id FROM Agent a WHERE a.appName = :appName
        )
        GROUP BY s.eventType
    """)
    fun sumByAppNameGroupByType(appName: String): List<Array<Any>>

    /** 최근 N분 윈도우 조회 */
    fun findAllByAgentIdAndEventTypeAndWindowStartAfter(
        agentId: Long, eventType: EventType, after: Instant
    ): List<EventStat>
}
