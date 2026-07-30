package com.logfriends.platform.overview.performance

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/overview/performance")
class PerformanceOverviewController(
    private val service: PerformanceOverviewService
) {
    @GetMapping
    fun getPerformanceOverview(
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<PerformanceOverviewResponse> =
        ResponseEntity.ok(service.getPerformanceOverview(from, to, appName, workerId, limit))
}
