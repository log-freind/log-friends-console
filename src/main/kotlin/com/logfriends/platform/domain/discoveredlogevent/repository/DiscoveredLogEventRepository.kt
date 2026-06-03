package com.logfriends.platform.domain.discoveredlogevent.repository

import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface DiscoveredLogEventRepository : JpaRepository<DiscoveredLogEvent, Long> {

    fun findByAgentIdAndEventNameAndSourceClassAndSourceMethod(
        agentId: Long,
        eventName: String,
        sourceClass: String,
        sourceMethod: String
    ): Optional<DiscoveredLogEvent>

    fun findAllByAgentIdOrderByEventNameAscSourceClassAscSourceMethodAsc(agentId: Long): List<DiscoveredLogEvent>

    fun findAllByAgentIdInOrderByEventNameAscSourceClassAscSourceMethodAsc(agentIds: Collection<Long>): List<DiscoveredLogEvent>
}
