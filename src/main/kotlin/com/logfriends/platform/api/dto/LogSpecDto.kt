package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.logspec.entity.LogSpecSnapshot
import com.logfriends.platform.domain.logspec.service.LogSpecRequest
import java.time.Instant

// === Request ===

data class LogSpecUpsertRequest(
    val specs: List<LogSpecItemRequest>
)

data class LogSpecItemRequest(
    val name: String,
    val description: String = "",
    val levels: List<String> = emptyList(),
    val category: String = "BUSINESS",
    val fields: List<Map<String, Any>> = emptyList()
) {
    fun toServiceRequest() = LogSpecRequest(name, description, levels, category, fields)
}

// === Response ===

data class LogSpecResponse(
    val id: Long,
    val agentId: Long,
    val specName: String,
    val description: String,
    val levels: List<String>,
    val category: String,
    val fields: List<Map<String, Any>>,
    val lastSeenAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(s: LogSpecSnapshot) = LogSpecResponse(
            id = s.id!!,
            agentId = s.agentId,
            specName = s.specName,
            description = s.description,
            levels = s.levels,
            category = s.category,
            fields = s.fields,
            lastSeenAt = s.lastSeenAt,
            updatedAt = s.updatedAt
        )
    }
}
