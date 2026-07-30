package com.logfriends.platform.overview.traffic

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
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
        if (!from.isBefore(to)) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "from must be before to")
        }
        if (limit !in 1..100) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "limit must be between 1 and 100")
        }

        val items = repository.findTopTraffic(
            from = from,
            to = to,
            appName = appName,
            workerId = workerId,
            limit = limit
        )
        return ResponseEntity.ok(TrafficOverviewResponse(items))
    }
}
