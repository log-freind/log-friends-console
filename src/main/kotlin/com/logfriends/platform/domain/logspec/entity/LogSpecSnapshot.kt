package com.logfriends.platform.domain.logspec.entity

import com.logfriends.platform.common.entity.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * SDK에서 등록된 LogSpec 정의를 스냅샷으로 저장
 * Agent가 heartbeat 시 자신의 LogSpec 목록을 함께 전송
 */
@Entity
@Table(
    name = "log_spec_snapshots",
    uniqueConstraints = [UniqueConstraint(columnNames = ["agent_id", "spec_name"])]
)
class LogSpecSnapshot(

    @Column(name = "agent_id", nullable = false)
    val agentId: Long,

    @Column(name = "spec_name", nullable = false)
    val specName: String,

    @Column(nullable = false)
    var description: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var levels: List<String> = emptyList(),

    @Column(nullable = false)
    var category: String = "BUSINESS",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var fields: List<Map<String, Any>> = emptyList(),

    @Column(nullable = false)
    var lastSeenAt: Instant = Instant.now()

) : BaseEntity() {

    fun update(
        description: String,
        levels: List<String>,
        category: String,
        fields: List<Map<String, Any>>
    ) {
        this.description = description
        this.levels = levels
        this.category = category
        this.fields = fields
        this.lastSeenAt = Instant.now()
    }
}
