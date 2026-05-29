package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.entity.AgentStatus
import com.logfriends.platform.domain.logspec.entity.LogSpecSnapshot
import jakarta.validation.constraints.NotBlank
import java.time.Instant

// === Request ===
data class AgentRegisterRequest(
    @field:NotBlank(message = "workerId는 필수입니다")
    val workerId: String,
    @field:NotBlank(message = "appName은 필수입니다")
    val appName: String,
    val sdkVersion: String? = null,
    val javaVersion: String? = null,
    val hostname: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class AgentUpdateRequest(
    val appName: String? = null,
    val sdkVersion: String? = null,
    val javaVersion: String? = null,
    val hostname: String? = null,
    val metadata: Map<String, Any>? = null
)

data class HeartbeatRequest(
    @field:NotBlank(message = "workerId는 필수입니다")
    val workerId: String,
    val metadata: Map<String, Any>? = null
)

// === Response ===
data class AgentResponse(
    val id: Long,
    val workerId: String,
    val appName: String,
    val status: AgentStatus,
    val sdkVersion: String?,
    val javaVersion: String?,
    val hostname: String?,
    val metadata: Map<String, Any>,
    val lastHeartbeat: Instant?,
    val registeredAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(agent: Agent) = AgentResponse(
            id = agent.id!!,
            workerId = agent.workerId,
            appName = agent.appName,
            status = agent.status,
            sdkVersion = agent.sdkVersion,
            javaVersion = agent.javaVersion,
            hostname = agent.hostname,
            metadata = agent.metadata,
            lastHeartbeat = agent.lastHeartbeat,
            registeredAt = agent.registeredAt,
            updatedAt = agent.updatedAt
        )
    }
}

data class AgentRegistrationResponse(
    val agentId: Long,
    val workerId: String,
    val appName: String,
    val status: AgentStatus,
    val sdkVersion: String?,
    val javaVersion: String?,
    val hostname: String?,
    val metadata: Map<String, Any>,
    val lastHeartbeat: Instant?,
    val registeredAt: Instant,
    val updatedAt: Instant,
    val knownLogSpecs: List<KnownLogSpecResponse>
) {
    companion object {
        fun from(agent: Agent, knownLogSpecs: List<LogSpecSnapshot>) = AgentRegistrationResponse(
            agentId = agent.id!!,
            workerId = agent.workerId,
            appName = agent.appName,
            status = agent.status,
            sdkVersion = agent.sdkVersion,
            javaVersion = agent.javaVersion,
            hostname = agent.hostname,
            metadata = agent.metadata,
            lastHeartbeat = agent.lastHeartbeat,
            registeredAt = agent.registeredAt,
            updatedAt = agent.updatedAt,
            knownLogSpecs = knownLogSpecs
                .distinctBy { it.specName }
                .sortedBy { it.specName }
                .map { KnownLogSpecResponse.from(it) }
        )
    }
}

data class KnownLogSpecResponse(
    val eventName: String,
    val updatedAt: Instant
) {
    companion object {
        fun from(spec: LogSpecSnapshot) = KnownLogSpecResponse(
            eventName = spec.specName,
            updatedAt = spec.updatedAt
        )
    }
}
