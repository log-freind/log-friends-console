package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.FieldRequestCreateRequest
import com.logfriends.platform.api.dto.FieldRequestResponse
import com.logfriends.platform.api.dto.FieldRequestStatusUpdateRequest
import com.logfriends.platform.domain.fieldrequest.service.FieldRequestService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/field-requests")
class FieldRequestController(
    private val fieldRequestService: FieldRequestService
) {

    @PostMapping
    fun create(@RequestBody request: FieldRequestCreateRequest): ResponseEntity<FieldRequestResponse> =
        ResponseEntity.ok(
            FieldRequestResponse.from(
                fieldRequestService.create(
                    appName = request.appName,
                    eventName = request.eventName,
                    requestedFieldName = request.requestedFieldName,
                    requestedType = request.requestedType,
                    reason = request.reason,
                    requestedBy = request.requestedBy
                )
            )
        )

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @RequestBody request: FieldRequestStatusUpdateRequest
    ): ResponseEntity<FieldRequestResponse> =
        ResponseEntity.ok(FieldRequestResponse.from(fieldRequestService.updateStatus(id, request.status)))
}
