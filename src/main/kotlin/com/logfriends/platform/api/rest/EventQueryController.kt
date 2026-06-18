package com.logfriends.platform.api.rest

import com.logfriends.platform.infrastructure.query.EventQueryService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/events")
class EventQueryController(
    private val eventQueryService: EventQueryService
) {

    @GetMapping("/http")
    fun queryHttp(
        @RequestParam(required = false) workerId: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(eventQueryService.queryHttpEvents(workerId, from, to))

    @GetMapping("/log")
    fun queryLog(
        @RequestParam(required = false) workerId: String?,
        @RequestParam(required = false) level: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(eventQueryService.queryLogEvents(workerId, level, from, to))

    @GetMapping("/jdbc")
    fun queryJdbc(
        @RequestParam(required = false) workerId: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) minDurationMs: Long?
    ): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(eventQueryService.queryJdbcEvents(workerId, from, to, minDurationMs))

    @GetMapping("/custom")
    fun queryCustom(
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(required = false) eventName: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false, defaultValue = "100") limit: Int
    ): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(eventQueryService.queryCustomEvents(appName, workerId, eventName, from, to, limit.coerceIn(1, 500)))

    @GetMapping("/custom.csv", produces = ["text/csv"])
    fun exportCustomCsv(
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(required = false) eventName: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): ResponseEntity<String> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"log-friends-custom-events.csv\"")
            .body(eventQueryService.queryCustomEventsCsv(appName, workerId, eventName, from, to))

    @GetMapping("/method-trace")
    fun queryMethodTrace(
        @RequestParam(required = false) workerId: String?,
        @RequestParam(required = false) className: String?,
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) minDurationMs: Long?
    ): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(eventQueryService.queryMethodTraceEvents(workerId, className, from, to, minDurationMs))
}
