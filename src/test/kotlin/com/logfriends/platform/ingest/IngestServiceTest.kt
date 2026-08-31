package com.logfriends.platform.ingest

import com.fasterxml.jackson.databind.ObjectMapper
import com.logfriends.platform.api.dto.EventPayload
import com.logfriends.platform.api.dto.IngestRequest
import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test

class IngestServiceTest {

    private val objectMapper = ObjectMapper()
    private val properties = IngestBatchProperties(dbBatchSize = 100)

    @Test
    fun `save handles legacy events without client metadata successfully`() {
        val executedQueries = mutableListOf<String>()
        val mockConnection = MockConnection { context ->
            executedQueries.add(context.sql())
            arrayOf(MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult()))
        }
        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val ingestService = IngestService(dsl, objectMapper, properties)

        val legacyEvent = EventPayload(
            type = "LOG_EVENT",
            timestamp = "2026-08-27T00:00:00Z",
            eventName = "orderCreated",
            payload = mapOf("orderId" to "ord-1")
        )
        val request = IngestRequest(
            workerId = "legacy-jvm-worker-1",
            events = listOf(legacyEvent)
        )

        val response = ingestService.save(request)
        assertThat(response.received).isEqualTo(1)
        assertThat(response.stored).isEqualTo(1)
        assertThat(response.failed).isEqualTo(0)
        assertThat(executedQueries).isNotEmpty()
        assertThat(executedQueries[0]).contains("custom_events")
    }

    @Test
    fun `save handles client events with eventId and sessionId`() {
        val executedQueries = mutableListOf<String>()
        val mockConnection = MockConnection { context ->
            executedQueries.add(context.sql())
            arrayOf(MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult()))
        }
        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val ingestService = IngestService(dsl, objectMapper, properties)

        val clientEvent = EventPayload(
            type = "LOG_EVENT",
            timestamp = "2026-08-27T00:00:00Z",
            eventName = "buttonClicked",
            payload = mapOf("btn" to "checkout"),
            eventId = "evt-uuid-1234",
            sessionId = "sess-uuid-5678",
            appInstanceId = "app-inst-999"
        )
        val request = IngestRequest(
            workerId = "browser-worker-1",
            events = listOf(clientEvent)
        )

        val response = ingestService.save(request)
        assertThat(response.received).isEqualTo(1)
        assertThat(response.stored).isEqualTo(1)
        assertThat(response.failed).isEqualTo(0)
        assertThat(executedQueries[0]).contains("custom_events")
    }

    @Test
    fun `save rejects batch size exceeding maximum limit with BATCH_TOO_LARGE`() {
        val dsl = DSL.using(MockConnection { arrayOf(MockResult(1, null)) }, SQLDialect.POSTGRES)
        val ingestService = IngestService(dsl, objectMapper, properties)

        val events = (1..51).map {
            EventPayload(
                type = "LOG_EVENT",
                timestamp = "2026-08-27T00:00:00Z",
                eventName = "item$it"
            )
        }
        val request = IngestRequest(workerId = "w-1", events = events)

        assertThatThrownBy { ingestService.save(request) }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.BATCH_TOO_LARGE)
    }

    @Test
    fun `save enforces rate limiter when threshold is exceeded`() {
        val dsl = DSL.using(MockConnection { arrayOf(MockResult(1, null)) }, SQLDialect.POSTGRES)
        val limiter = IngestRateLimiter(maxRequestsPerMinute = 2)
        val ingestService = IngestService(dsl, objectMapper, properties, rateLimiter = limiter)

        val event = EventPayload(type = "LOG_EVENT", timestamp = "2026-08-27T00:00:00Z", eventName = "eventA")
        val req = IngestRequest(workerId = "rate-worker", events = listOf(event))

        // 1st request ok
        ingestService.save(req, "127.0.0.1")
        // 2nd request ok
        ingestService.save(req, "127.0.0.1")

        // 3rd request should fail rate limit
        assertThatThrownBy { ingestService.save(req, "127.0.0.1") }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED)
    }

    @Test
    fun `save accurately dedupes duplicate events in same batch without false stored count`() {
        val mockConnection = MockConnection { _ ->
            arrayOf(MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult()))
        }
        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val ingestService = IngestService(dsl, objectMapper, properties)

        // 2 events with identical eventId and timestamp
        val event1 = EventPayload(
            type = "LOG_EVENT",
            timestamp = "2026-08-27T00:00:00Z",
            eventName = "itemClicked",
            eventId = "duplicate-uuid-999"
        )
        val event2 = EventPayload(
            type = "LOG_EVENT",
            timestamp = "2026-08-27T00:00:00Z",
            eventName = "itemClicked",
            eventId = "duplicate-uuid-999"
        )
        val request = IngestRequest(workerId = "w-1", events = listOf(event1, event2))

        val response = ingestService.save(request)
        assertThat(response.received).isEqualTo(2)
        assertThat(response.stored).isEqualTo(1) // Only 1 unique stored
        assertThat(response.failed).isEqualTo(0)
    }

    @Test
    fun `save separates invalid event metadata into failed count`() {
        val executedQueries = mutableListOf<String>()
        val mockConnection = MockConnection { context ->
            executedQueries.add(context.sql())
            arrayOf(MockResult(1, DSL.using(SQLDialect.POSTGRES).newResult()))
        }
        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val ingestService = IngestService(dsl, objectMapper, properties)

        val invalidEvent = EventPayload(
            type = "LOG_EVENT",
            timestamp = "2026-08-27T00:00:00Z",
            eventName = "notCamelCase_name",
            eventId = "evt-1"
        )
        val request = IngestRequest(
            workerId = "browser-worker-1",
            events = listOf(invalidEvent)
        )

        val response = ingestService.save(request)
        assertThat(response.received).isEqualTo(1)
        assertThat(response.stored).isEqualTo(0)
        assertThat(response.failed).isEqualTo(1)
        assertThat(executedQueries[0]).contains("ingest_failed_events")
    }
}
