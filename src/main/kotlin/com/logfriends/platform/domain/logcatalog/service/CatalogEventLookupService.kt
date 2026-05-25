package com.logfriends.platform.domain.logcatalog.service

import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.logspec.repository.LogSpecSnapshotRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

@Service
class CatalogEventLookupService(
    private val agentRepository: AgentRepository,
    private val logSpecSnapshotRepository: LogSpecSnapshotRepository,
    private val dsl: DSLContext
) {

    fun exists(appName: String, eventName: String): Boolean {
        val agents = agentRepository.findByAppName(appName)
        if (agents.isEmpty()) return false

        if (logSpecSnapshotRepository.findAllByAppName(appName).any { it.specName == eventName }) {
            return true
        }

        val workerIds = agents.map { it.workerId }
        if (workerIds.isEmpty()) return false

        return dsl.fetchExists(
            DSL.selectOne()
                .from(DSL.table("custom_events"))
                .where(DSL.field("worker_id").`in`(workerIds))
                .and(DSL.field("event_name").eq(eventName))
        )
    }
}
