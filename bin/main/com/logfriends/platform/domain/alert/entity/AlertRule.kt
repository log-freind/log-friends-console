package com.logfriends.platform.domain.alert.entity

import com.logfriends.platform.common.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "alert_rules")
class AlertRule(

    @Column(nullable = false)
    var name: String,

    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var severity: Severity = Severity.WARNING,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var condition: Map<String, Any>,

    @Column(columnDefinition = "text[]")
    var notifyChannels: Array<String> = emptyArray(),

    @Column(nullable = false)
    var cooldownMinutes: Int = 30,

    @Column(nullable = false)
    var enabled: Boolean = true,

    var createdBy: Long? = null,

    var lastTriggered: Instant? = null

) : BaseEntity() {

    fun enable() { this.enabled = true }

    fun disable() { this.enabled = false }

    fun markTriggered() {
        this.lastTriggered = Instant.now()
    }

    /**
     * 쿨다운 기간이 지났는지 확인 (발화 가능 여부)
     */
    fun canTrigger(): Boolean {
        val lt = lastTriggered ?: return true
        return Instant.now().isAfter(lt.plusSeconds(cooldownMinutes.toLong() * 60))
    }
}

enum class Severity {
    CRITICAL, WARNING, INFO
}
