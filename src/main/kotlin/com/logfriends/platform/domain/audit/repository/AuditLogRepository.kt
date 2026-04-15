package com.logfriends.platform.domain.audit.repository

import com.logfriends.platform.domain.audit.entity.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface AuditLogRepository : JpaRepository<AuditLog, Long> {

    fun findByEntityTypeAndEntityId(entityType: String, entityId: Long, pageable: Pageable): Page<AuditLog>

    fun findByUserId(userId: Long, pageable: Pageable): Page<AuditLog>

    fun findByCreatedAtBetween(from: Instant, to: Instant, pageable: Pageable): Page<AuditLog>
}
