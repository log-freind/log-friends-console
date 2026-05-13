package com.logfriends.platform.domain.agent.entity

import com.logfriends.platform.common.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "agents")
class Agent(

    @Column(nullable = false, unique = true)
    val workerId: String,

    @Column(nullable = false)
    var appName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AgentStatus = AgentStatus.UNKNOWN,

    var sdkVersion: String? = null,

    var javaVersion: String? = null,

    var hostname: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var metadata: Map<String, Any> = emptyMap(),

    var lastHeartbeat: Instant? = null,

    @Column(nullable = false, updatable = false)
    val registeredAt: Instant = Instant.now()

) : BaseEntity() {

    fun heartbeat() {
        this.lastHeartbeat = Instant.now()
        this.status = AgentStatus.RUNNING
    }

    fun markStopped() {
        this.status = AgentStatus.STOPPED
    }

    fun updateInfo(
        appName: String? = null,
        sdkVersion: String? = null,
        javaVersion: String? = null,
        hostname: String? = null,
        metadata: Map<String, Any>? = null
    ) {
        appName?.let { this.appName = it }
        sdkVersion?.let { this.sdkVersion = it }
        javaVersion?.let { this.javaVersion = it }
        hostname?.let { this.hostname = it }
        metadata?.let { this.metadata = it }
    }
}

enum class AgentStatus {
    RUNNING, STOPPED, UNKNOWN
}
