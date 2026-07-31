package com.logfriends.platform.ingest

import com.logfriends.platform.api.dto.EventPayload

internal class IngestEventPartitioner(
    private val validator: IngestValidator
) {
    fun partition(workerId: String, events: List<EventPayload>): IngestEventPartition {
        val validEventsByType = linkedMapOf<String, MutableList<EventPayload>>()
        val invalidEvents = mutableListOf<InvalidIngestEvent>()

        events.forEach { event ->
            val failureReason = validator.validate(workerId, event)
            if (failureReason != null) {
                invalidEvents += InvalidIngestEvent(event, failureReason)
            } else {
                validEventsByType.getOrPut(event.type) { mutableListOf() } += event
            }
        }

        return IngestEventPartition(validEventsByType, invalidEvents)
    }
}

internal data class IngestEventPartition(
    val validEventsByType: Map<String, List<EventPayload>>,
    val invalidEvents: List<InvalidIngestEvent>
)

internal data class InvalidIngestEvent(
    val event: EventPayload,
    val reason: IngestFailureReason
)
