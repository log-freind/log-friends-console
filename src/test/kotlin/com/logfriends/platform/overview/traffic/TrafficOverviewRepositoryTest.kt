package com.logfriends.platform.overview.traffic

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test
import java.time.Instant

class TrafficOverviewRepositoryTest {
    @Test
    fun `groups traffic and joins agents when appName is supplied`() {
        var executedSql = ""
        val connection = MockConnection { context ->
            executedSql = context.sql()
            val dsl = DSL.using(SQLDialect.POSTGRES)
            val method = DSL.field("method", String::class.java)
            val uri = DSL.field("uri", String::class.java)
            val requestCount = DSL.field("request_count", Long::class.java)
            val result = dsl.newResult(method, uri, requestCount)
            result.add(dsl.newRecord(method, uri, requestCount).apply {
                set(method, "GET")
                set(uri, "/x")
                set(requestCount, 3L)
            })
            arrayOf(MockResult(1, result))
        }
        val repository = TrafficOverviewRepository(DSL.using(connection, SQLDialect.POSTGRES))

        val items = repository.findTopTraffic(
            from = Instant.parse("2026-07-30T00:00:00Z"),
            to = Instant.parse("2026-07-30T01:00:00Z"),
            appName = "orders",
            workerId = "worker-1",
            limit = 10
        )

        assertThat(items).containsExactly(TrafficOverviewItem("GET", "/x", 3))
        assertThat(executedSql).contains("join agents as \"a\"")
        assertThat(executedSql).contains("a.worker_id = h.worker_id")
        assertThat(executedSql).contains("a.app_name = ?")
        assertThat(executedSql).contains("h.worker_id = ?")
        assertThat(executedSql).contains("h.ts >= cast(? as timestamp with time zone)")
        assertThat(executedSql).contains("h.ts < cast(? as timestamp with time zone)")
        assertThat(executedSql).contains("group by h.method, h.uri")
        assertThat(executedSql).contains("fetch next ? rows only")
    }

    @Test
    fun `does not join agents without appName`() {
        var executedSql = ""
        val connection = MockConnection { context ->
            executedSql = context.sql()
            arrayOf(MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult()))
        }
        val repository = TrafficOverviewRepository(DSL.using(connection, SQLDialect.POSTGRES))

        repository.findTopTraffic(
            from = Instant.parse("2026-07-30T00:00:00Z"),
            to = Instant.parse("2026-07-30T01:00:00Z"),
            appName = null,
            workerId = null,
            limit = 10
        )

        assertThat(executedSql).doesNotContain("join agents")
    }
}
