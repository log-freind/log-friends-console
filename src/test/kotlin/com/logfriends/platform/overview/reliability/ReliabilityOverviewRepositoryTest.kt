package com.logfriends.platform.overview.reliability

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

class ReliabilityOverviewRepositoryTest {
    @Test
    fun `applies app and worker filters to HTTP and ingest queries`() {
        val queries = mutableListOf<CapturedQuery>()
        val mockConnection = MockConnection { context ->
            queries.add(CapturedQuery(context.sql(), context.bindings().toList()))
            arrayOf(MockResult(1, resultFor(context.sql())))
        }
        val repository = ReliabilityOverviewRepository(
            DSL.using(mockConnection, SQLDialect.POSTGRES)
        )
        val from = Instant.parse("2026-07-30T00:00:00Z")
        val to = Instant.parse("2026-07-31T00:00:00Z")

        val summary = repository.getHttpSummary(from, to, "orders", "worker-1")
        val failedEvents = repository.getFailedEventCount(from, to, "orders", "worker-1")
        val httpErrors = repository.getTopHttpErrors(from, to, "orders", "worker-1", 10)
        val ingestFailures = repository.getTopIngestFailures(from, to, "orders", "worker-1", 10)

        assertThat(summary).isEqualTo(HttpCounts(20, 5))
        assertThat(failedEvents).isEqualTo(3)
        assertThat(httpErrors).containsExactly(TopHttpError("GET", "/orders", 500, 4))
        assertThat(ingestFailures).containsExactly(TopIngestFailure("INVALID_TIMESTAMP", 3))
        assertThat(queries).hasSize(4)
        assertThat(queries).allSatisfy { query ->
            assertThat(query.sql).contains("agents")
            assertThat(query.bindings).contains("orders", "worker-1")
        }
        assertThat(listOf(queries[0].sql, queries[2].sql)).allSatisfy { sql ->
            assertThat(sql).contains("http_events")
        }
        assertThat(listOf(queries[1].sql, queries[3].sql)).allSatisfy { sql ->
            assertThat(sql).contains("ingest_failed_events")
        }
        assertThat(queries[2].sql).contains("status_code")
        assertThat(queries[2].bindings).contains(400)
    }

    private fun resultFor(sql: String): Result<Record> {
        val dsl = DSL.using(SQLDialect.POSTGRES)
        return when {
            sql.contains("failure_count") -> result(
                dsl,
                listOf(
                    DSL.field("reason_code", String::class.java),
                    DSL.field("failure_count", Long::class.java)
                ),
                listOf("INVALID_TIMESTAMP", 3L)
            )
            sql.contains("total_requests") -> result(
                dsl,
                listOf(
                    DSL.field("total_requests", Long::class.java),
                    DSL.field("error_requests", Long::class.java)
                ),
                listOf(20L, 5L)
            )
            sql.contains("failed_events") -> result(
                dsl,
                listOf(DSL.field("failed_events", Long::class.java)),
                listOf(3L)
            )
            sql.contains("error_count") -> result(
                dsl,
                listOf(
                    DSL.field("method", String::class.java),
                    DSL.field("uri", String::class.java),
                    DSL.field("status_code", Int::class.java),
                    DSL.field("error_count", Long::class.java)
                ),
                listOf("GET", "/orders", 500, 4L)
            )
            else -> error("Unexpected query: $sql")
        }
    }

    private fun result(
        dsl: DSLContext,
        fields: List<Field<*>>,
        values: List<Any>
    ): Result<Record> {
        val result = dsl.newResult(*fields.toTypedArray())
        val record = dsl.newRecord(*fields.toTypedArray())
        values.forEachIndexed { index, value ->
            @Suppress("UNCHECKED_CAST")
            record.set(fields[index] as Field<Any>, value)
        }
        result.add(record)
        return result
    }

    private data class CapturedQuery(
        val sql: String,
        val bindings: List<Any?>
    )
}
