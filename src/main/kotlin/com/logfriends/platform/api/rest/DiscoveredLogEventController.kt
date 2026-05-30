package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.DiscoveredLogEventReportRequest
import com.logfriends.platform.api.dto.DiscoveredLogEventReportResponse
import com.logfriends.platform.api.dto.DiscoveredLogEventResponse
import com.logfriends.platform.domain.discoveredlogevent.service.DiscoveredLogEventService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/agents/{agentId}/discovered-log-events")
class DiscoveredLogEventController(
    private val discoveredLogEventService: DiscoveredLogEventService
) {

    @PostMapping
    fun report(
        @PathVariable agentId: Long,
        @Valid @RequestBody request: DiscoveredLogEventReportRequest
    ): ResponseEntity<DiscoveredLogEventReportResponse> =
        ResponseEntity.ok(discoveredLogEventService.report(agentId, request))

    @GetMapping
    fun findAll(@PathVariable agentId: Long): ResponseEntity<List<DiscoveredLogEventResponse>> =
        ResponseEntity.ok(
            discoveredLogEventService.findAll(agentId)
                .map { DiscoveredLogEventResponse.from(it) }
        )
}
