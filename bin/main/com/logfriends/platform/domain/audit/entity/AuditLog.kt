package com.logfriends.platform.domain.audit.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "audit_logs")
class AuditLog(

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val userEmail: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: AuditAction,

    @Column(nullable = false)
    val entityType: String,

    val entityId: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val changes: Map<String, Any> = emptyMap(),

    val ipAddress: String? = null,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}

enum class AuditAction {
    CREATE, UPDATE, DELETE, STATUS_CHANGE, LOGIN
}
