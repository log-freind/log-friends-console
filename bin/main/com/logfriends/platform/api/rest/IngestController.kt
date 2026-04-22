package com.logfriends.platform.api.rest

import com.logfriends.platform.ingest.IngestRequest
import com.logfriends.platform.ingest.IngestService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ingest")
class IngestController(private val ingestService: IngestService) {

    @PostMapping
    fun ingest(@RequestBody request: IngestRequest): ResponseEntity<Void> {
        ingestService.save(request)
        return ResponseEntity.ok().build()
    }
}
