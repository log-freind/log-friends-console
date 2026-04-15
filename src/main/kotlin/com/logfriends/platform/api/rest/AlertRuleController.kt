package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.*
import com.logfriends.platform.domain.alert.service.AlertRuleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/alert-rules")
class AlertRuleController(
    private val alertRuleService: AlertRuleService
) {

    @GetMapping
    fun listRules(): ResponseEntity<List<AlertRuleResponse>> {
        val rules = alertRuleService.findAll().map { AlertRuleResponse.from(it) }
        return ResponseEntity.ok(rules)
    }

    @GetMapping("/{id}")
    fun getRule(@PathVariable id: Long): ResponseEntity<AlertRuleResponse> {
        val rule = alertRuleService.findById(id)
        return ResponseEntity.ok(AlertRuleResponse.from(rule))
    }

    @PostMapping
    fun createRule(@Valid @RequestBody request: AlertRuleCreateRequest): ResponseEntity<AlertRuleResponse> {
        val rule = alertRuleService.create(
            name = request.name,
            description = request.description,
            severity = request.severity,
            condition = request.condition,
            notifyChannels = request.notifyChannels,
            cooldownMinutes = request.cooldownMinutes
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertRuleResponse.from(rule))
    }

    @PatchMapping("/{id}")
    fun updateRule(
        @PathVariable id: Long,
        @RequestBody request: AlertRuleUpdateRequest
    ): ResponseEntity<AlertRuleResponse> {
        val rule = alertRuleService.update(
            id = id,
            name = request.name,
            description = request.description,
            severity = request.severity,
            condition = request.condition,
            notifyChannels = request.notifyChannels,
            cooldownMinutes = request.cooldownMinutes
        )
        return ResponseEntity.ok(AlertRuleResponse.from(rule))
    }

    @PatchMapping("/{id}/toggle")
    fun toggleRule(@PathVariable id: Long): ResponseEntity<AlertRuleResponse> {
        val rule = alertRuleService.toggleEnabled(id)
        return ResponseEntity.ok(AlertRuleResponse.from(rule))
    }

    @DeleteMapping("/{id}")
    fun deleteRule(@PathVariable id: Long): ResponseEntity<Void> {
        alertRuleService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
