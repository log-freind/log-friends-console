package com.logfriends.platform.domain.eventstat.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.eventstat.entity.EventStat
import com.logfriends.platform.domain.eventstat.entity.EventType
import com.logfriends.platform.domain.eventstat.repository.EventStatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class EventStatService(
    private val eventStatRepository: EventStatRepository,
    private val agentRepository: AgentRepository
) {

    fun findByAgent(agentId: Long, from: Instant?, to: Instant?): List<EventStat> {
        if (!agentRepository.existsById(agentId)) throw BusinessException(ErrorCode.AGENT_NOT_FOUND)
        return if (from != null && to != null) {
            eventStatRepository.findAllByAgentIdAndWindowStartBetween(agentId, from, to)
        } else {
            eventStatRepository.findAllByAgentId(agentId)
        }
    }

    /** Agent별 이벤트 타입 요약 */
    fun summarizeByAgent(agentId: Long): List<EventTypeSummary> {
        if (!agentRepository.existsById(agentId)) throw BusinessException(ErrorCode.AGENT_NOT_FOUND)
        return eventStatRepository.sumByAgentIdGroupByType(agentId).map {
            EventTypeSummary(
                eventType = it[0] as EventType,
                totalCount = (it[1] as Long),
                totalErrorCount = (it[2] as Long)
            )
        }
    }

    /** appName별 이벤트 타입 요약 */
    fun summarizeByApp(appName: String): List<EventTypeSummary> =
        eventStatRepository.sumByAppNameGroupByType(appName).map {
            EventTypeSummary(
                eventType = it[0] as EventType,
                totalCount = (it[1] as Long),
                totalErrorCount = (it[2] as Long)
            )
        }

    @Transactional
    fun record(agentId: Long, requests: List<EventStatRequest>) {
        if (!agentRepository.existsById(agentId)) throw BusinessException(ErrorCode.AGENT_NOT_FOUND)
        val stats = requests.map {
            EventStat(
                agentId = agentId,
                eventType = it.eventType,
                windowStart = it.windowStart,
                count = it.count,
                errorCount = it.errorCount,
                avgDurationMs = it.avgDurationMs
            )
        }
        eventStatRepository.saveAll(stats)
    }
}

data class EventTypeSummary(
    val eventType: EventType,
    val totalCount: Long,
    val totalErrorCount: Long
)

data class EventStatRequest(
    val eventType: EventType,
    val windowStart: Instant,
    val count: Long,
    val errorCount: Long = 0,
    val avgDurationMs: Double? = null
)
