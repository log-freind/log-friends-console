package com.logfriends.platform.domain.discoveredlogevent.entity

import com.logfriends.platform.common.entity.BaseEntity
import com.logfriends.platform.domain.agent.entity.Agent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "discovered_log_events",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_discovered_log_events_source",
            columnNames = ["agent_id", "event_name", "source_class", "source_method"]
        )
    ],
    indexes = [
        Index(name = "idx_discovered_log_events_agent_status", columnList = "agent_id, status"),
        Index(name = "idx_discovered_log_events_event_name", columnList = "event_name")
    ]
)
class DiscoveredLogEvent(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    val agent: Agent,

    @Column(name = "event_name", nullable = false)
    val eventName: String,

    @Column(name = "source_class", nullable = false)
    val sourceClass: String,

    @Column(name = "source_method", nullable = false)
    val sourceMethod: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_names", columnDefinition = "jsonb", nullable = false)
    var parameterNames: List<String> = emptyList(),

    @Column(name = "app_version")
    var appVersion: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DiscoveredLogEventStatus = DiscoveredLogEventStatus.DISCOVERED,

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    val firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now()
) : BaseEntity() {

    fun refresh(
        parameterNames: List<String>,
        appVersion: String?,
        observedAt: Instant = Instant.now()
    ) {
        this.parameterNames = parameterNames
        this.appVersion = appVersion
        this.lastSeenAt = observedAt
    }
}
