package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.*
import com.logfriends.platform.domain.alert.entity.Severity
import com.logfriends.platform.domain.incident.entity.IncidentStatus
import com.logfriends.platform.domain.incident.service.IncidentService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/incidents")
class IncidentController(
    private val incidentService: IncidentService
) {

    /**
     * 인시던트 동적 검색 (Specification + 페이징)
     * GET /api/incidents?status=TRIGGERED&severity=CRITICAL&from=2024-04-01T00:00:00Z&page=0&size=20
     */
    @GetMapping
    fun searchIncidents(
        @RequestParam(required = false) status: IncidentStatus?,
        @RequestParam(required = false) severity: Severity?,
        @RequestParam(required = false) ruleId: Long?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @PageableDefault(size = 20, sort = ["triggeredAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): ResponseEntity<Page<IncidentResponse>> {
        val page = incidentService.search(status, severity, ruleId, from, to, pageable)
            .map { IncidentResponse.from(it) }
        return ResponseEntity.ok(page)
    }

    @GetMapping("/{id}")
    fun getIncident(@PathVariable id: Long): ResponseEntity<IncidentResponse> {
        val incident = incidentService.findById(id)
        return ResponseEntity.ok(IncidentResponse.from(incident))
    }

    @PostMapping
    fun triggerIncident(@Valid @RequestBody request: IncidentTriggerRequest): ResponseEntity<IncidentResponse> {
        val incident = incidentService.trigger(request.ruleId, request.title)
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponse.from(incident))
    }

    @PatchMapping("/{id}/acknowledge")
    fun acknowledgeIncident(
        @PathVariable id: Long,
        @Valid @RequestBody request: IncidentAckRequest
    ): ResponseEntity<IncidentResponse> {
        val incident = incidentService.acknowledge(id, request.userId)
        return ResponseEntity.ok(IncidentResponse.from(incident))
    }

    @PatchMapping("/{id}/resolve")
    fun resolveIncident(
        @PathVariable id: Long,
        @Valid @RequestBody request: IncidentResolveRequest
    ): ResponseEntity<IncidentResponse> {
        val incident = incidentService.resolve(id, request.userId, request.summary)
        return ResponseEntity.ok(IncidentResponse.from(incident))
    }

    @GetMapping("/count")
    fun countByStatus(): ResponseEntity<IncidentCountResponse> {
        val counts = incidentService.countByStatus()
        return ResponseEntity.ok(
            IncidentCountResponse(
                triggered = counts[IncidentStatus.TRIGGERED] ?: 0,
                acknowledged = counts[IncidentStatus.ACKNOWLEDGED] ?: 0,
                resolved = counts[IncidentStatus.RESOLVED] ?: 0
            )
        )
    }
}
