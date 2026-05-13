package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.*
import com.logfriends.platform.domain.agent.service.AgentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/agents")
class AgentController(
    private val agentService: AgentService
) {

    @GetMapping
    fun listAgents(): ResponseEntity<List<AgentResponse>> {
        val agents = agentService.findAll().map { AgentResponse.from(it) }
        return ResponseEntity.ok(agents)
    }

    @GetMapping("/{id}")
    fun getAgent(@PathVariable id: Long): ResponseEntity<AgentResponse> {
        val agent = agentService.findById(id)
        return ResponseEntity.ok(AgentResponse.from(agent))
    }

    @PostMapping
    fun registerAgent(@Valid @RequestBody request: AgentRegisterRequest): ResponseEntity<AgentResponse> {
        val agent = agentService.register(
            workerId = request.workerId,
            appName = request.appName,
            metadata = request.metadata
        )
        agent.updateInfo(
            sdkVersion = request.sdkVersion,
            javaVersion = request.javaVersion,
            hostname = request.hostname
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(AgentResponse.from(agent))
    }

    @PatchMapping("/{id}")
    fun updateAgent(
        @PathVariable id: Long,
        @RequestBody request: AgentUpdateRequest
    ): ResponseEntity<AgentResponse> {
        val agent = agentService.updateAgent(
            id = id,
            appName = request.appName,
            sdkVersion = request.sdkVersion,
            javaVersion = request.javaVersion,
            hostname = request.hostname,
            metadata = request.metadata
        )
        return ResponseEntity.ok(AgentResponse.from(agent))
    }

    @PostMapping("/heartbeat")
    fun heartbeat(@Valid @RequestBody request: HeartbeatRequest): ResponseEntity<AgentResponse> {
        val agent = agentService.heartbeat(request.workerId, request.metadata)
        return ResponseEntity.ok(AgentResponse.from(agent))
    }

    @DeleteMapping("/{id}")
    fun deleteAgent(@PathVariable id: Long): ResponseEntity<Void> {
        agentService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
