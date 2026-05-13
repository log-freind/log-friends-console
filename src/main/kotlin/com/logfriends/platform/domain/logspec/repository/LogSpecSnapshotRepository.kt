package com.logfriends.platform.domain.logspec.repository

import com.logfriends.platform.domain.logspec.entity.LogSpecSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LogSpecSnapshotRepository : JpaRepository<LogSpecSnapshot, Long> {

    fun findAllByAgentId(agentId: Long): List<LogSpecSnapshot>

    fun findByAgentIdAndSpecName(agentId: Long, specName: String): LogSpecSnapshot?

    @Query("""
        SELECT s FROM LogSpecSnapshot s
        WHERE s.agentId IN (
            SELECT a.id FROM Agent a WHERE a.appName = :appName
        )
    """)
    fun findAllByAppName(appName: String): List<LogSpecSnapshot>

    fun deleteAllByAgentId(agentId: Long)
}
