package com.logfriends.platform.domain.overview.business

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test
import java.time.Instant

class BusinessOverviewServiceTest {

    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-02T00:00:00Z")

    @Test
    fun `aggregates custom events and joins agents for appName filter`() {
        var executedSql = ""
        val connection = MockConnection { context ->
            executedSql = context.sql()
            val dsl = DSL.using(SQLDialect.POSTGRES)
            val eventName = DSL.field("eventName", String::class.java)
            val eventCount = DSL.field("eventCount", Long::class.java)
            val result = dsl.newResult(eventName, eventCount)
            result.add(dsl.newRecord(eventName, eventCount).apply {
                set(eventName, "cartItemAdded")
                set(eventCount, 10L)
            })
            arrayOf(MockResult(1, result))
        }
        val service = BusinessOverviewService(DSL.using(connection, SQLDialect.POSTGRES))

        val items = service.getOverview(from, to, "shop", "worker-1", 10)

        assertThat(items).containsExactly(BusinessEventCount("cartItemAdded", 10))
        assertThat(executedSql).contains(
            "from \"custom_events\" as \"c\"",
            "join \"agents\" as \"a\"",
            "\"a\".\"worker_id\" = \"c\".\"worker_id\"",
            "\"a\".\"app_name\" = ?",
            "\"c\".\"worker_id\" = ?",
            "group by \"c\".\"event_name\"",
            "fetch next ? rows only"
        )
    }

    @Test
    fun `does not join agents without appName filter`() {
        var executedSql = ""
        val connection = MockConnection { context ->
            executedSql = context.sql()
            arrayOf(MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult()))
        }
        val service = BusinessOverviewService(DSL.using(connection, SQLDialect.POSTGRES))

        service.getOverview(from, to, null, null, 10)

        assertThat(executedSql).doesNotContain("join \"agents\"")
    }
}
