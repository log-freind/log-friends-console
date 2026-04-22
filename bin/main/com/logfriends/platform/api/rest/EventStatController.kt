package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.*
import com.logfriends.platform.domain.eventstat.service.EventStatService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/event-stats")
class EventStatController(
    private val eventStatService: EventStatService
) {

    /** Agent별 이벤트 통계 목록 (시간 범위 필터 가능) */
    @GetMapping("/by-agent/{agentId}")
    fun listByAgent(
        @PathVariable agentId: Long,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?
    ): ResponseEntity<List<EventStatResponse>> {
        val stats = eventStatService.findByAgent(agentId, from, to).map { EventStatResponse.from(it) }
        return ResponseEntity.ok(stats)
    }

    /** Agent별 이벤트 타입 요약 (빅데이터 엔지니어용 카탈로그) */
    @GetMapping("/summary/by-agent/{agentId}")
    fun summaryByAgent(@PathVariable agentId: Long): ResponseEntity<List<EventTypeSummaryResponse>> {
        val summary = eventStatService.summarizeByAgent(agentId).map { EventTypeSummaryResponse.from(it) }
        return ResponseEntity.ok(summary)
    }

    /** appName별 이벤트 타입 요약 */
    @GetMapping("/summary/by-app")
    fun summaryByApp(@RequestParam appName: String): ResponseEntity<List<EventTypeSummaryResponse>> {
        val summary = eventStatService.summarizeByApp(appName).map { EventTypeSummaryResponse.from(it) }
        return ResponseEntity.ok(summary)
    }

    /** Agent가 이벤트 통계를 전송 (Spark 또는 SDK heartbeat에서 호출) */
    @PostMapping("/by-agent/{agentId}")
    fun record(
        @PathVariable agentId: Long,
        @RequestBody request: EventStatRecordRequest
    ): ResponseEntity<Void> {
        eventStatService.record(agentId, request.stats.map { it.toServiceRequest() })
        return ResponseEntity.noContent().build()
    }
}
