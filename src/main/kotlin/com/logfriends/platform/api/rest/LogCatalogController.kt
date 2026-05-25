package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.LogCatalogAppsResponse
import com.logfriends.platform.api.dto.LogCatalogEventsResponse
import com.logfriends.platform.domain.logcatalog.service.LogCatalogService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/log-catalog")
class LogCatalogController(
    private val logCatalogService: LogCatalogService
) {

    @GetMapping("/apps")
    fun listApps(): ResponseEntity<LogCatalogAppsResponse> =
        ResponseEntity.ok(logCatalogService.listApps())

    @GetMapping("/apps/{appName}/events")
    fun listEvents(
        @PathVariable appName: String,
        @RequestParam(required = false) workerId: String?,
        @RequestParam(required = false) sampleSize: Int?
    ): ResponseEntity<LogCatalogEventsResponse> =
        ResponseEntity.ok(logCatalogService.listEvents(appName, workerId, sampleSize))
}
