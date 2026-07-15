package com.logfriends.platform.domain.logcatalog.service

import com.logfriends.platform.api.dto.LogCatalogFieldResponse
import com.logfriends.platform.api.dto.LogCatalogMismatchCode
import com.logfriends.platform.api.dto.LogCatalogMismatchResponse
import org.springframework.stereotype.Component

@Component
class LogCatalogMismatchCalculator {
    fun calculate(
        fields: List<LogCatalogFieldResponse>,
        payload: Map<String, Any?>
    ): List<LogCatalogMismatchResponse> {
        if (fields.isEmpty()) return emptyList()

        val specFieldNames = fields.map { it.name }.toSet()
        val requiredFieldNames = fields.filter { it.required }.map { it.name }.toSet()
        val payloadFieldNames = payload.keys

        val extraFields = (payloadFieldNames - specFieldNames)
            .map { LogCatalogMismatchResponse(LogCatalogMismatchCode.EXTRA_FIELD, it) }
        val missingFields = (requiredFieldNames - payloadFieldNames)
            .map { LogCatalogMismatchResponse(LogCatalogMismatchCode.MISSING_FIELD, it) }

        return (extraFields + missingFields).sortedBy { it.fieldName }
    }
}
