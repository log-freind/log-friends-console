package com.logfriends.platform.infrastructure.query

import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Result
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test
import java.time.Instant

class EventQueryServiceTest {

    @Test
    fun `queryCustomEvents applies sessionId filter and returns client metadata`() {
        val executedQueries = mutableListOf<String>()
        val mockConnection = MockConnection { context ->
            executedQueries.add(context.sql())
            val dsl = DSL.using(SQLDialect.POSTGRES)
            val fields = listOf(
                DSL.field("id", Long::class.java),
                DSL.field("appName", String::class.java),
                DSL.field("workerId", String::class.java),
                DSL.field("sourceType", String::class.java),
                DSL.field("timestamp", String::class.java),
                DSL.field("receivedAt", String::class.java),
                DSL.field("eventType", String::class.java),
                DSL.field("eventName", String::class.java),
                DSL.field("eventId", String::class.java),
                DSL.field("sessionId", String::class.java),
                DSL.field("appInstanceId", String::class.java),
                DSL.field("payload", String::class.java)
            )
            val record = dsl.newRecord(*fields.toTypedArray())
            record.set(fields[0] as Field<Any>, 101L)
            record.set(fields[1] as Field<Any>, "shop-web")
            record.set(fields[2] as Field<Any>, "browser-worker-1")
            record.set(fields[3] as Field<Any>, "BROWSER")
            record.set(fields[4] as Field<Any>, "2026-08-27T06:00:00Z")
            record.set(fields[5] as Field<Any>, "2026-08-27T06:00:01Z")
            record.set(fields[6] as Field<Any>, "LOG_EVENT")
            record.set(fields[7] as Field<Any>, "itemAdded")
            record.set(fields[8] as Field<Any>, "evt-uuid-1")
            record.set(fields[9] as Field<Any>, "sess-uuid-1")
            record.set(fields[10] as Field<Any>, "")
            record.set(fields[11] as Field<Any>, "{\"item\":\"shoe\"}")

            val result = dsl.newResult(*fields.toTypedArray())
            result.add(record)

            arrayOf(MockResult(1, result))
        }

        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val service = EventQueryService(dsl)

        val rows = service.queryCustomEvents(
            appName = "shop-web",
            workerId = "browser-worker-1",
            eventName = "itemAdded",
            from = Instant.parse("2026-08-27T00:00:00Z"),
            to = Instant.parse("2026-08-27T23:59:59Z"),
            limit = 50,
            sessionId = "sess-uuid-1"
        )

        assertThat(rows).hasSize(1)
        assertThat(rows[0]["appName"]).isEqualTo("shop-web")
        assertThat(rows[0]["sourceType"]).isEqualTo("BROWSER")
        assertThat(rows[0]["sessionId"]).isEqualTo("sess-uuid-1")
        assertThat(rows[0]["eventId"]).isEqualTo("evt-uuid-1")

        assertThat(executedQueries[0]).contains("custom_events")
        assertThat(executedQueries[0]).contains("session_id")
    }

    @Test
    fun `queryCustomEventsCsv formats headers and client metadata fields correctly`() {
        val mockConnection = MockConnection { _ ->
            val dsl = DSL.using(SQLDialect.POSTGRES)
            val fields = listOf(
                DSL.field("id", Long::class.java),
                DSL.field("appName", String::class.java),
                DSL.field("workerId", String::class.java),
                DSL.field("sourceType", String::class.java),
                DSL.field("timestamp", String::class.java),
                DSL.field("receivedAt", String::class.java),
                DSL.field("eventType", String::class.java),
                DSL.field("eventName", String::class.java),
                DSL.field("eventId", String::class.java),
                DSL.field("sessionId", String::class.java),
                DSL.field("appInstanceId", String::class.java),
                DSL.field("payload", String::class.java)
            )
            val record = dsl.newRecord(*fields.toTypedArray())
            record.set(fields[0] as Field<Any>, 1L)
            record.set(fields[1] as Field<Any>, "web-shop")
            record.set(fields[2] as Field<Any>, "browser-worker-1")
            record.set(fields[3] as Field<Any>, "BROWSER")
            record.set(fields[4] as Field<Any>, "2026-08-27T06:00:00Z")
            record.set(fields[5] as Field<Any>, "2026-08-27T06:00:01Z")
            record.set(fields[6] as Field<Any>, "LOG_EVENT")
            record.set(fields[7] as Field<Any>, "checkoutCompleted")
            record.set(fields[8] as Field<Any>, "evt-5678")
            record.set(fields[9] as Field<Any>, "sess-1234")
            record.set(fields[10] as Field<Any>, "")
            record.set(fields[11] as Field<Any>, "{\"total\":50000}")

            val result = dsl.newResult(*fields.toTypedArray())
            result.add(record)

            arrayOf(MockResult(1, result))
        }

        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val eventQueryService = EventQueryService(dsl)

        val csv = eventQueryService.queryCustomEventsCsv(
            appName = "web-shop",
            workerId = "browser-worker-1",
            eventName = "checkoutCompleted",
            from = Instant.parse("2026-08-27T00:00:00Z"),
            to = Instant.parse("2026-08-27T23:59:59Z"),
            sessionId = "sess-1234"
        )

        val lines = csv.trim().lines()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("timestamp,receivedAt,appName,workerId,sourceType,sessionId,eventId,appInstanceId,eventType,eventName,payloadJson")
        assertThat(lines[1]).contains("2026-08-27T06:00:00Z")
        assertThat(lines[1]).contains("BROWSER")
        assertThat(lines[1]).contains("sess-1234")
        assertThat(lines[1]).contains("evt-5678")
    }

    @Test
    fun `queryCustomEventsCsv defaults null or unregistered sourceType to UNKNOWN`() {
        val mockConnection = MockConnection { _ ->
            val dsl = DSL.using(SQLDialect.POSTGRES)
            val fields = listOf(
                DSL.field("id", Long::class.java),
                DSL.field("appName", String::class.java),
                DSL.field("workerId", String::class.java),
                DSL.field("sourceType", String::class.java),
                DSL.field("timestamp", String::class.java),
                DSL.field("receivedAt", String::class.java),
                DSL.field("eventType", String::class.java),
                DSL.field("eventName", String::class.java),
                DSL.field("eventId", String::class.java),
                DSL.field("sessionId", String::class.java),
                DSL.field("appInstanceId", String::class.java),
                DSL.field("payload", String::class.java)
            )
            val record = dsl.newRecord(*fields.toTypedArray())
            record.set(fields[0] as Field<Any>, 2L)
            record.set(fields[1] as Field<Any>, "unregistered-app")
            record.set(fields[2] as Field<Any>, "unregistered-worker")
            // sourceType is null in DB
            record.set(fields[4] as Field<Any>, "2026-08-27T06:00:00Z")
            record.set(fields[5] as Field<Any>, "2026-08-27T06:00:01Z")
            record.set(fields[6] as Field<Any>, "LOG_EVENT")
            record.set(fields[7] as Field<Any>, "rawEvent")

            val result = dsl.newResult(*fields.toTypedArray())
            result.add(record)

            arrayOf(MockResult(1, result))
        }

        val dsl = DSL.using(mockConnection, SQLDialect.POSTGRES)
        val eventQueryService = EventQueryService(dsl)

        val csv = eventQueryService.queryCustomEventsCsv(
            appName = "unregistered-app",
            workerId = "unregistered-worker",
            eventName = "rawEvent",
            from = Instant.parse("2026-08-27T00:00:00Z"),
            to = Instant.parse("2026-08-27T23:59:59Z")
        )

        val lines = csv.trim().lines()
        assertThat(lines).hasSize(2)
        assertThat(lines[1]).contains("UNKNOWN")
        assertThat(lines[1]).doesNotContain("JVM")
    }
}
