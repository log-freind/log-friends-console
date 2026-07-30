package com.logfriends.platform.overview.traffic

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/overview/traffic")
class TrafficOverviewController(
    private val repository: TrafficOverviewRepository
) {
    @GetMapping
    fun getTraffic(
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<TrafficOverviewResponse> {
        val items = repository.findTopTraffic(
            from = from,
            to = to,
            appName = appName,
            workerId = workerId,
            limit = limit.coerceIn(1, 100)
        )
        return ResponseEntity.ok(TrafficOverviewResponse(items))
    }
}
