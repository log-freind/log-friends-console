package com.logfriends.platform.domain.discoveredlogevent.service

import com.logfriends.platform.api.dto.DiscoveredLogEventItemRequest
import com.logfriends.platform.api.dto.DiscoveredLogEventReportRequest
import com.logfriends.platform.api.dto.DiscoveredLogEventReportResponse
import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEvent
import com.logfriends.platform.domain.discoveredlogevent.repository.DiscoveredLogEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class DiscoveredLogEventService(
    private val agentRepository: AgentRepository,
    private val discoveredLogEventRepository: DiscoveredLogEventRepository
) {

    @Transactional
    fun report(agentId: Long, request: DiscoveredLogEventReportRequest): DiscoveredLogEventReportResponse {
        val agent = findAgent(agentId)
        validateAgentMatch(agent, request)
        validateBatchSize(request.events)

        val observedAt = Instant.now()
        request.events.forEach { event ->
            validateEvent(event)
            upsert(agent, event, request.appVersion, observedAt)
        }

        return DiscoveredLogEventReportResponse(
            received = request.events.size,
            upserted = request.events.size
        )
    }

    fun findAll(agentId: Long): List<DiscoveredLogEvent> {
        findAgent(agentId)
        return discoveredLogEventRepository.findAllByAgentIdOrderByEventNameAscSourceClassAscSourceMethodAsc(agentId)
    }

    private fun upsert(
        agent: Agent,
        event: DiscoveredLogEventItemRequest,
        appVersion: String?,
        observedAt: Instant
    ) {
        val existing = discoveredLogEventRepository.findByAgentIdAndEventNameAndSourceClassAndSourceMethod(
            agentId = agent.id!!,
            eventName = event.eventName,
            sourceClass = event.sourceClass,
            sourceMethod = event.sourceMethod
        )

        val discoveredLogEvent = existing
            .map {
                it.refresh(
                    parameterNames = event.parameterNames,
                    appVersion = appVersion,
                    observedAt = observedAt
                )
                it
            }
            .orElseGet {
                DiscoveredLogEvent(
                    agent = agent,
                    eventName = event.eventName,
                    sourceClass = event.sourceClass,
                    sourceMethod = event.sourceMethod,
                    parameterNames = event.parameterNames,
                    appVersion = appVersion,
                    firstSeenAt = observedAt,
                    lastSeenAt = observedAt
                )
            }

        discoveredLogEventRepository.save(discoveredLogEvent)
    }

    private fun findAgent(agentId: Long): Agent =
        agentRepository.findById(agentId)
            .orElseThrow { BusinessException(ErrorCode.AGENT_NOT_FOUND) }

    private fun validateAgentMatch(agent: Agent, request: DiscoveredLogEventReportRequest) {
        if (agent.workerId != request.workerId || agent.appName != request.appName) {
            throw BusinessException(ErrorCode.CONFLICT, "agent identity does not match workerId/appName")
        }
    }

    private fun validateBatchSize(events: List<DiscoveredLogEventItemRequest>) {
        if (events.size > MAX_EVENTS_PER_REPORT) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "events must be less than or equal to $MAX_EVENTS_PER_REPORT")
        }
    }

    private fun validateEvent(event: DiscoveredLogEventItemRequest) {
        if (event.eventName.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "eventName is required")
        }
        if (!EVENT_NAME_PATTERN.matches(event.eventName)) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "eventName must be camelCase")
        }
        if (event.sourceClass.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "sourceClass is required")
        }
        if (event.sourceMethod.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "sourceMethod is required")
        }
    }

    companion object {
        private const val MAX_EVENTS_PER_REPORT = 500
        private val EVENT_NAME_PATTERN = Regex("^[a-z][a-zA-Z0-9]*$")
    }
}
