package com.logfriends.platform.api.rest.overview.business

import com.logfriends.platform.domain.overview.business.BusinessOverviewService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/overview/business")
class BusinessOverviewController(
    private val businessOverviewService: BusinessOverviewService
) {

    @GetMapping
    fun getOverview(
        @RequestParam from: Instant,
        @RequestParam to: Instant,
        @RequestParam(required = false) appName: String?,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<BusinessOverviewResponse> {
        val items = businessOverviewService.getOverview(
            from = from,
            to = to,
            appName = appName,
            workerId = workerId,
            limit = limit.coerceIn(1, 100)
        )
        return ResponseEntity.ok(BusinessOverviewResponse(items.map(BusinessOverviewItem::from)))
    }
}
