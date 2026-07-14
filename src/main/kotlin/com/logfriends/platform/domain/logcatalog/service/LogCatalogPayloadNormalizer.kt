package com.logfriends.platform.domain.logcatalog.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class LogCatalogPayloadNormalizer(
    private val objectMapper: ObjectMapper
) {
    fun normalizeForFieldComparison(payload: Map<String, Any?>): Map<String, Any?> {
        val wrappedValue = payload["value"]
        val wrapperType = payload["type"]?.toString()

        if (wrapperType == "jsonb" && wrappedValue is String) {
            return parseObject(wrappedValue) ?: payload
        }

        return payload
    }

    private fun parseObject(value: String): Map<String, Any?>? {
        if (value.isBlank()) return null
        return runCatching {
            objectMapper.readValue(value, object : TypeReference<Map<String, Any?>>() {})
        }.getOrNull()
    }
}
