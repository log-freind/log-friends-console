package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.IngestRequest
import com.logfriends.platform.api.dto.IngestResponse
import com.logfriends.platform.ingest.IngestService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ingest")
class IngestController(private val ingestService: IngestService) {

    @PostMapping
    fun ingest(
        @RequestBody request: IngestRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<IngestResponse> {
        val clientIp = httpRequest.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: httpRequest.remoteAddr
        return ResponseEntity.ok(ingestService.save(request, clientIp))
    }
}
