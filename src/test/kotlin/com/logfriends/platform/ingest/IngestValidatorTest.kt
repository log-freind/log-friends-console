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

    @Test
    fun `returns INVALID_EVENT_ID when eventId is blank or exceeds 100 chars`() {
        val blankResult = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated", eventId = " ")
        )
        assertThat(blankResult).isEqualTo(IngestFailureReason.INVALID_EVENT_ID)

        val longResult = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated", eventId = "x".repeat(101))
        )
        assertThat(longResult).isEqualTo(IngestFailureReason.INVALID_EVENT_ID)
    }

    @Test
    fun `returns INVALID_SESSION_ID when sessionId is blank or exceeds 100 chars`() {
        val blankResult = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated", sessionId = " ")
        )
        assertThat(blankResult).isEqualTo(IngestFailureReason.INVALID_SESSION_ID)

        val longResult = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated", sessionId = "s".repeat(101))
        )
        assertThat(longResult).isEqualTo(IngestFailureReason.INVALID_SESSION_ID)
    }

    @Test
    fun `returns INVALID_APP_INSTANCE_ID when appInstanceId is blank or exceeds 100 chars`() {
        val blankResult = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated", appInstanceId = " ")
        )
        assertThat(blankResult).isEqualTo(IngestFailureReason.INVALID_APP_INSTANCE_ID)

        val longResult = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "orderCreated", appInstanceId = "a".repeat(101))
        )
        assertThat(longResult).isEqualTo(IngestFailureReason.INVALID_APP_INSTANCE_ID)
    }

    @Test
    fun `returns null when event contains valid client metadata`() {
        val result = validator.validate(
            workerId = "browser-worker-1",
            event = event(
                type = "LOG_EVENT",
                eventName = "orderCreated",
                eventId = "3e54cf21-b3b3-4f9e-9988-123456789abc",
                sessionId = "sess-8899",
                appInstanceId = "app-inst-1"
            )
        )
        assertThat(result).isNull()
    }

    @Test
    fun `returns EVENT_TOO_LARGE when event byte size exceeds 32KB`() {
        val largePayload = mapOf("largeText" to "a".repeat(33 * 1024))
        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "largeEvent", payload = largePayload)
        )
        assertThat(result).isEqualTo(IngestFailureReason.EVENT_TOO_LARGE)
    }

    @Test
    fun `returns PAYLOAD_TOO_DEEP when payload depth exceeds 8 levels`() {
        // Create 10 levels deep nested map
        var deepMap: Map<String, Any> = mapOf("val" to "leaf")
        for (i in 1..9) {
            deepMap = mapOf("nest$i" to deepMap)
        }

        val result = validator.validate(
            workerId = "worker-1",
            event = event(type = "LOG_EVENT", eventName = "deepEvent", payload = deepMap)
        )
        assertThat(result).isEqualTo(IngestFailureReason.PAYLOAD_TOO_DEEP)
    }

    private fun event(
        type: String,
        timestamp: String = "2026-05-13T00:00:00Z",
        eventName: String? = null,
        eventId: String? = null,
        sessionId: String? = null,
        appInstanceId: String? = null,
        payload: Map<String, Any>? = null
    ): EventPayload =
        EventPayload(
            type = type,
            timestamp = timestamp,
            eventName = eventName,
            eventId = eventId,
            sessionId = sessionId,
            appInstanceId = appInstanceId,
            payload = payload
        )
}
