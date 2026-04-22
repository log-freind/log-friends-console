package com.logfriends.platform.domain.audit.service

import com.logfriends.platform.domain.audit.entity.AuditAction
import com.logfriends.platform.domain.audit.entity.AuditLog
import com.logfriends.platform.domain.audit.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 이벤트
 * ApplicationEvent로 발행 → AuditService가 비동기 저장
 */
data class AuditEvent(
    val userId: Long,
    val userEmail: String,
    val action: AuditAction,
    val entityType: String,
    val entityId: Long? = null,
    val changes: Map<String, Any> = emptyMap(),
    val ipAddress: String? = null
)

@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 비동기 이벤트 리스너 — 메인 트랜잭션과 독립
     * Propagation.REQUIRES_NEW → 감사 로그 저장 실패해도 원본 트랜잭션에 영향 없음
     */
    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleAuditEvent(event: AuditEvent) {
        try {
            val auditLog = AuditLog(
                userId = event.userId,
                userEmail = event.userEmail,
                action = event.action,
                entityType = event.entityType,
                entityId = event.entityId,
                changes = event.changes,
                ipAddress = event.ipAddress
            )
            auditLogRepository.save(auditLog)
            log.debug("[Audit] {} {} {} (entity: {}#{})",
                event.userEmail, event.action, event.entityType, event.entityType, event.entityId)
        } catch (e: Exception) {
            log.error("[Audit] Failed to save audit log: {}", e.message, e)
        }
    }
}
