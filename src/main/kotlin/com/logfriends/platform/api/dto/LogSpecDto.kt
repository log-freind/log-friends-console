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
    val apiMethod: String? = null,
    val apiPath: String? = null,
    val apiDescription: String? = null,
    val levels: List<String> = emptyList(),
    val category: String = "BUSINESS",
    val fields: List<Map<String, Any>> = emptyList()
) {
    fun toServiceRequest() = LogSpecRequest(
        name = name,
        description = description,
        apiMethod = apiMethod?.trim()?.takeIf { it.isNotBlank() },
        apiPath = apiPath?.trim()?.takeIf { it.isNotBlank() },
        apiDescription = apiDescription?.trim()?.takeIf { it.isNotBlank() },
        levels = levels,
        category = category,
        fields = fields
    )
}

// === Response ===

data class LogSpecResponse(
    val id: Long,
    val agentId: Long,
    val specName: String,
    val description: String,
    val apiMethod: String?,
    val apiPath: String?,
    val apiDescription: String?,
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
            apiMethod = s.apiMethod,
            apiPath = s.apiPath,
            apiDescription = s.apiDescription,
            levels = s.levels,
            category = s.category,
            fields = s.fields,
            lastSeenAt = s.lastSeenAt,
            updatedAt = s.updatedAt
        )
    }
}
