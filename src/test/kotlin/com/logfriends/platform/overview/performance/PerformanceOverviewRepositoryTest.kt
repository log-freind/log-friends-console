package com.logfriends.platform.overview.performance

import org.assertj.core.api.Assertions.assertThat
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockExecuteContext
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test
import java.time.Instant

class PerformanceOverviewRepositoryTest {
    @Test
    fun `builds a bound PostgreSQL percentile query ranked by p95`() {
        lateinit var execution: MockExecuteContext
        val result = DSL.using(SQLDialect.POSTGRES).newResult(
            DSL.field("method", SQLDataType.VARCHAR),
            DSL.field("uri", SQLDataType.VARCHAR),
            DSL.field("request_count", SQLDataType.BIGINT),
            DSL.field("average_duration_ms", SQLDataType.DOUBLE),
            DSL.field("p95_duration_ms", SQLDataType.DOUBLE),
            DSL.field("max_duration_ms", SQLDataType.BIGINT)
        )
        val connection = MockConnection { context ->
            execution = context
            arrayOf(MockResult(0, result))
        }
        val repository = PerformanceOverviewRepository(DSL.using(connection, SQLDialect.POSTGRES))
        val from = Instant.parse("2026-07-30T00:00:00Z")
        val to = Instant.parse("2026-07-30T01:00:00Z")

        val items = repository.findSlowEndpoints(from, to, "orders", "worker-1", 25)

        assertThat(items).isEmpty()
        assertThat(execution.sql().lowercase().replace("\"", ""))
            .contains("percentile_cont(?) within group (order by h.duration_ms)")
            .contains("left outer join agents as a")
            .contains("group by h.method, h.uri")
            .contains("order by p95_duration_ms desc")
        assertThat(execution.bindings().toList())
            .containsExactly(
                0.95,
                "2026-07-30 00:00:00+00:00",
                "2026-07-30 01:00:00+00:00",
                "orders",
                "worker-1",
                25L
            )
    }
}
