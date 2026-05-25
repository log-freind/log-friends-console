package com.logfriends.platform.api.dto

import com.logfriends.platform.domain.fieldrequest.entity.FieldRequest
import com.logfriends.platform.domain.fieldrequest.entity.FieldRequestStatus
import com.logfriends.platform.domain.fieldrequest.entity.FieldType
import java.time.Instant

data class FieldRequestCreateRequest(
    val appName: String,
    val eventName: String,
    val requestedFieldName: String,
    val requestedType: FieldType,
    val reason: String,
    val requestedBy: String? = null
)

data class FieldRequestStatusUpdateRequest(
    val status: FieldRequestStatus
)

data class FieldRequestResponse(
    val id: Long,
    val requestedFieldName: String,
    val requestedType: FieldType,
    val reason: String,
    val requestedBy: String?,
    val status: FieldRequestStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(fieldRequest: FieldRequest): FieldRequestResponse =
            FieldRequestResponse(
                id = fieldRequest.id!!,
                requestedFieldName = fieldRequest.requestedFieldName,
                requestedType = fieldRequest.requestedType,
                reason = fieldRequest.reason,
                requestedBy = fieldRequest.requestedBy,
                status = fieldRequest.status,
                createdAt = fieldRequest.createdAt,
                updatedAt = fieldRequest.updatedAt
            )
    }
}
