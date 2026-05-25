package com.logfriends.platform.domain.fieldrequest.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.fieldrequest.entity.FieldRequest
import com.logfriends.platform.domain.fieldrequest.entity.FieldRequestStatus
import com.logfriends.platform.domain.fieldrequest.entity.FieldType
import com.logfriends.platform.domain.fieldrequest.repository.FieldRequestRepository
import com.logfriends.platform.domain.logcatalog.service.CatalogEventLookupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FieldRequestService(
    private val fieldRequestRepository: FieldRequestRepository,
    private val agentRepository: AgentRepository,
    private val catalogEventLookupService: CatalogEventLookupService
) {

    @Transactional
    fun create(
        appName: String,
        eventName: String,
        requestedFieldName: String,
        requestedType: FieldType,
        reason: String,
        requestedBy: String?
    ): FieldRequest {
        val command = validateCreateCommand(
            appName = appName,
            eventName = eventName,
            requestedFieldName = requestedFieldName,
            reason = reason,
            requestedBy = requestedBy
        )

        if (agentRepository.findByAppName(command.appName).isEmpty()) {
            throw BusinessException(ErrorCode.NOT_FOUND, "appName not found")
        }
        if (!catalogEventLookupService.exists(command.appName, command.eventName)) {
            throw BusinessException(ErrorCode.NOT_FOUND, "eventName not found")
        }

        val openStatuses = listOf(FieldRequestStatus.REQUESTED, FieldRequestStatus.ACCEPTED)
        if (fieldRequestRepository.existsByAppNameAndEventNameAndRequestedFieldNameAndStatusIn(
                command.appName,
                command.eventName,
                command.requestedFieldName,
                openStatuses
            )
        ) {
            throw BusinessException(ErrorCode.CONFLICT, "open field request already exists")
        }

        return fieldRequestRepository.save(
            FieldRequest(
                appName = command.appName,
                eventName = command.eventName,
                requestedFieldName = command.requestedFieldName,
                requestedType = requestedType,
                reason = command.reason,
                requestedBy = command.requestedBy
            )
        )
    }

    @Transactional
    fun updateStatus(id: Long, status: FieldRequestStatus): FieldRequest {
        val fieldRequest = fieldRequestRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND, "field request not found") }

        try {
            fieldRequest.transitionTo(status)
        } catch (ex: IllegalArgumentException) {
            throw BusinessException(ErrorCode.CONFLICT, ex.message ?: "invalid status transition")
        }

        return fieldRequestRepository.save(fieldRequest)
    }

    private fun validateCreateCommand(
        appName: String,
        eventName: String,
        requestedFieldName: String,
        reason: String,
        requestedBy: String?
    ): ValidatedCreateCommand {
        val normalizedAppName = appName.trim()
        val normalizedEventName = eventName.trim()
        val normalizedFieldName = requestedFieldName.trim()
        val normalizedReason = reason.trim()

        requireNotBlank(normalizedAppName, "appName")
        requireNotBlank(normalizedEventName, "eventName")
        requireNotBlank(normalizedFieldName, "requestedFieldName")
        requireNotBlank(normalizedReason, "reason")

        return ValidatedCreateCommand(
            appName = normalizedAppName,
            eventName = normalizedEventName,
            requestedFieldName = normalizedFieldName,
            reason = normalizedReason,
            requestedBy = requestedBy?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun requireNotBlank(value: String, fieldName: String) {
        if (value.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "$fieldName is required")
        }
    }

    private data class ValidatedCreateCommand(
        val appName: String,
        val eventName: String,
        val requestedFieldName: String,
        val reason: String,
        val requestedBy: String?
    )
}
