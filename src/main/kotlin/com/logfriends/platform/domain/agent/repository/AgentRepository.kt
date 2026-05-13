package com.logfriends.platform.domain.agent.repository

import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.entity.AgentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.*

interface AgentRepository : JpaRepository<Agent, Long> {

    fun findByWorkerId(workerId: String): Optional<Agent>

    fun existsByWorkerId(workerId: String): Boolean

    fun findByStatus(status: AgentStatus): List<Agent>

    @Query("SELECT a FROM Agent a WHERE a.lastHeartbeat < :threshold AND a.status = 'RUNNING'")
    fun findStaleAgents(threshold: Instant): List<Agent>

    fun findByAppNameContainingIgnoreCase(appName: String): List<Agent>
}
