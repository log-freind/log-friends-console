package com.logfriends.platform.ingest

import com.logfriends.platform.api.dto.EventPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IngestValidatorTest {
    private val validator = IngestValidator()

    @Test
    fun `returns null for valid HTTP event`() {
        val event = event(type = "HTTP")

        val result = validator.validate(workerId = "worker-1", event = event)

        assertThat(result).isNull()
    }

    @Test
    fun `returns MISSING_WORKER_ID when workerId is blank`() {
        val result = validator.validate(workerId = " ", event = event(type = "HTTP"))

        assertThat(result).isEqualTo(IngestFailureReason.MISSING_WORKER_ID)
    }

    @Test
    fun `returns MISSING_TYPE when type is blank`() {
        val result = validator.validate(workerId = "worker-1", event = event(type = " "))

        assertThat(result).isEqualTo(IngestFailureReason.MISSING_TYPE)
    }

    @Test
    fun `returns UNKNOWN_TYPE when type is unsupported`() {
        val result = validator.validate(workerId = "worker-1", event = event(type = "UNKNOWN"))

        assertThat(result).isEqualTo(IngestFailureReason.UNKNOWN_TYPE)
    }

    @Test
    fun `returns MISSING_TIMESTAMP when timestamp is blank`() {
        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "HTTP", timestamp = " ")
        )

        assertThat(result).isEqualTo(IngestFailureReason.MISSING_TIMESTAMP)
    }

    @Test
    fun `returns INVALID_TIMESTAMP when timestamp is not ISO instant`() {
        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "HTTP", timestamp = "2026-05-13 12:00:00")
        )

        assertThat(result).isEqualTo(IngestFailureReason.INVALID_TIMESTAMP)
    }

    @Test
    fun `returns MISSING_EVENT_NAME when LOG_EVENT eventName is blank`() {
        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = " ")
        )

        assertThat(result).isEqualTo(IngestFailureReason.MISSING_EVENT_NAME)
    }

    @Test
    fun `returns INVALID_EVENT_NAME when LOG_EVENT eventName is not camelCase`() {
        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "order.created")
        )

        assertThat(result).isEqualTo(IngestFailureReason.INVALID_EVENT_NAME)
    }

    @Test
    fun `returns null when LOG_EVENT has camelCase eventName`() {
        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated")
        )

        assertThat(result).isNull()
    }

    private fun event(
        type: String,
        timestamp: String = "2026-05-13T00:00:00Z",
        eventName: String? = null
    ): EventPayload =
        EventPayload(
            type = type,
            timestamp = timestamp,
            eventName = eventName
        )
}
