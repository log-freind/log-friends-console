package com.logfriends.platform.domain.agent.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.entity.AgentStatus
import com.logfriends.platform.domain.agent.repository.AgentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional(readOnly = true)
class AgentService(
    private val agentRepository: AgentRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findAll(): List<Agent> = agentRepository.findAll()

    fun findById(id: Long): Agent =
        agentRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.AGENT_NOT_FOUND) }

    fun findByWorkerId(workerId: String): Agent =
        agentRepository.findByWorkerId(workerId)
            .orElseThrow { BusinessException(ErrorCode.AGENT_NOT_FOUND) }

    @Transactional
    fun register(workerId: String, appName: String, metadata: Map<String, Any> = emptyMap()): Agent {
        if (agentRepository.existsByWorkerId(workerId)) {
            throw BusinessException(ErrorCode.AGENT_ALREADY_REGISTERED)
        }

        val agent = Agent(
            workerId = workerId,
            appName = appName,
            metadata = metadata,
            status = AgentStatus.RUNNING,
            lastHeartbeat = Instant.now()
        )
        return agentRepository.save(agent)
    }

    @Transactional
    fun heartbeat(workerId: String, metadata: Map<String, Any>? = null): Agent {
        val agent = agentRepository.findByWorkerId(workerId)
            .orElseGet {
                // 등록 안 된 에이전트가 heartbeat 보내면 자동 등록
                Agent(workerId = workerId, appName = "unknown")
            }

        agent.heartbeat()
        metadata?.let { agent.updateInfo(metadata = it) }
        return agentRepository.save(agent)
    }

    @Transactional
    fun updateAgent(id: Long, appName: String?, sdkVersion: String?,
                    javaVersion: String?, hostname: String?,
                    metadata: Map<String, Any>?): Agent {
        val agent = findById(id)
        agent.updateInfo(appName, sdkVersion, javaVersion, hostname, metadata)
        return agentRepository.save(agent)
    }

    @Transactional
    fun delete(id: Long) {
        val agent = findById(id)
        agentRepository.delete(agent)
    }

    /**
     * 5분 이상 heartbeat 없는 에이전트를 STOPPED로 변경
     * 매 1분마다 실행
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    fun checkStaleAgents() {
        val threshold = Instant.now().minusSeconds(300) // 5분
        val staleAgents = agentRepository.findStaleAgents(threshold)

        staleAgents.forEach { agent ->
            log.info("[Platform] Agent {} marked as STOPPED (no heartbeat since {})",
                agent.workerId, agent.lastHeartbeat)
            agent.markStopped()
        }
        if (staleAgents.isNotEmpty()) {
            agentRepository.saveAll(staleAgents)
        }
    }
}
