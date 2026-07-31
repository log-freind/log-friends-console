package com.logfriends.platform.ingest

import com.logfriends.platform.api.dto.EventPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IngestEventPartitionerTest {
    private val partitioner = IngestEventPartitioner(IngestValidator())

    @Test
    fun `groups valid events by type and separates invalid events`() {
        val result = partitioner.partition(
            workerId = "worker-1",
            events = listOf(
                event(type = "HTTP"),
                event(type = "LOG_EVENT", eventName = "cartItemAdded"),
                event(type = "HTTP"),
                event(type = "LOG_EVENT", timestamp = "invalid", eventName = "cartItemAdded")
            )
        )

        assertThat(result.validEventsByType["HTTP"]).hasSize(2)
        assertThat(result.validEventsByType["LOG_EVENT"]).hasSize(1)
        assertThat(result.invalidEvents)
            .singleElement()
            .extracting(InvalidIngestEvent::reason)
            .isEqualTo(IngestFailureReason.INVALID_TIMESTAMP)
    }

    private fun event(
        type: String,
        timestamp: String = "2026-08-01T00:00:00Z",
        eventName: String? = null
    ) = EventPayload(
        type = type,
        timestamp = timestamp,
        eventName = eventName
    )
}
