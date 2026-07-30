package com.logfriends.platform.overview.reliability

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/overview")
class ReliabilityOverviewController(
    private val service: ReliabilityOverviewService
) {
    @GetMapping("/reliability")
    fun getReliabilityOverview(
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(required = false, defaultValue = "10") limit: Int
    ): ResponseEntity<ReliabilityOverviewResponse> {
        val parsedFrom = parseRequiredInstant("from", from)
        val parsedTo = parseRequiredInstant("to", to)
        return ResponseEntity.ok(
            service.getOverview(parsedFrom, parsedTo, appName, workerId, limit)
        )
    }

    private fun parseRequiredInstant(name: String, value: String?): Instant {
        if (value.isNullOrBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "$name is required")
        }
        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "$name must be an ISO-8601 instant")
        }
    }
}
