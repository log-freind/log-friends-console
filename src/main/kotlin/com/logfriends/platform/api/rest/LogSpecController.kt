package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.LogSpecResponse
import com.logfriends.platform.api.dto.LogSpecUpsertRequest
import com.logfriends.platform.domain.logspec.service.LogSpecService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/log-specs")
class LogSpecController(
    private val logSpecService: LogSpecService
) {

    /** 전체 LogSpec 목록 (빅데이터 엔지니어가 전체 카탈로그 조회) */
    @GetMapping
    fun listAll(): ResponseEntity<List<LogSpecResponse>> {
        val specs = logSpecService.findAll().map { LogSpecResponse.from(it) }
        return ResponseEntity.ok(specs)
    }

    /** appName 기준으로 해당 앱의 LogSpec 조회 */
    @GetMapping("/by-app")
    fun listByApp(@RequestParam appName: String): ResponseEntity<List<LogSpecResponse>> {
        val specs = logSpecService.findAllByAppName(appName).map { LogSpecResponse.from(it) }
        return ResponseEntity.ok(specs)
    }

    /** 특정 Agent의 LogSpec 조회 */
    @GetMapping("/by-agent/{agentId}")
    fun listByAgent(@PathVariable agentId: Long): ResponseEntity<List<LogSpecResponse>> {
        val specs = logSpecService.findAllByAgent(agentId).map { LogSpecResponse.from(it) }
        return ResponseEntity.ok(specs)
    }

    /** Agent가 자신의 LogSpec을 등록/갱신 (heartbeat 시 호출) */
    @PutMapping("/by-agent/{agentId}")
    fun upsertSpecs(
        @PathVariable agentId: Long,
        @RequestBody request: LogSpecUpsertRequest
    ): ResponseEntity<Void> {
        logSpecService.upsertSpecs(agentId, request.specs.map { it.toServiceRequest() })
        return ResponseEntity.noContent().build()
    }
}
