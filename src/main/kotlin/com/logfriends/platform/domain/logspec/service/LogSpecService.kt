package com.logfriends.platform.domain.logspec.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.logspec.entity.LogSpecSnapshot
import com.logfriends.platform.domain.logspec.repository.LogSpecSnapshotRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class LogSpecService(
    private val logSpecSnapshotRepository: LogSpecSnapshotRepository,
    private val agentRepository: AgentRepository
) {

    fun findAllByAgent(agentId: Long): List<LogSpecSnapshot> {
        if (!agentRepository.existsById(agentId)) {
            throw BusinessException(ErrorCode.AGENT_NOT_FOUND)
        }
        return logSpecSnapshotRepository.findAllByAgentId(agentId)
    }

    fun findAllByAppName(appName: String): List<LogSpecSnapshot> =
        logSpecSnapshotRepository.findAllByAppName(appName)

    fun findAll(): List<LogSpecSnapshot> =
        logSpecSnapshotRepository.findAll()

    /**
     * Agent heartbeat 시 LogSpec 목록을 upsert
     * 같은 agentId + specName이면 업데이트, 없으면 신규 저장
     */
    @Transactional
    fun upsertSpecs(agentId: Long, specs: List<LogSpecRequest>) {
        if (!agentRepository.existsById(agentId)) {
            throw BusinessException(ErrorCode.AGENT_NOT_FOUND)
        }
        specs.forEach { req ->
            val existing = logSpecSnapshotRepository.findByAgentIdAndSpecName(agentId, req.name)
            if (existing != null) {
                existing.update(req.description, req.levels, req.category, req.fields)
            } else {
                logSpecSnapshotRepository.save(
                    LogSpecSnapshot(
                        agentId = agentId,
                        specName = req.name,
                        description = req.description,
                        levels = req.levels,
                        category = req.category,
                        fields = req.fields
                    )
                )
            }
        }
    }

    @Transactional
    fun deleteAllByAgent(agentId: Long) {
        logSpecSnapshotRepository.deleteAllByAgentId(agentId)
    }
}

data class LogSpecRequest(
    val name: String,
    val description: String = "",
    val levels: List<String> = emptyList(),
    val category: String = "BUSINESS",
    val fields: List<Map<String, Any>> = emptyList()
)
