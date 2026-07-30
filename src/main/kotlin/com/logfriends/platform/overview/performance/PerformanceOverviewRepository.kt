package com.logfriends.platform.overview.performance

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class PerformanceOverviewRepository(
    private val dsl: DSLContext
) {
    fun findSlowEndpoints(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): List<PerformanceOverviewItem> {
        val method = DSL.field("h.method", String::class.java)
        val uri = DSL.field("h.uri", String::class.java)
        val durationMs = DSL.field("h.duration_ms", Long::class.java)
        val requestCount = DSL.count().cast(Long::class.java).`as`("request_count")
        val averageDurationMs = DSL.avg(durationMs).cast(Double::class.java).`as`("average_duration_ms")
        val p95DurationMs = DSL
            .percentileCont(0.95)
            .withinGroupOrderBy(durationMs)
            .cast(Double::class.java)
            .`as`("p95_duration_ms")
        val maxDurationMs = DSL.max(durationMs).`as`("max_duration_ms")

        val conditions = mutableListOf<Condition>(
            DSL.field("h.ts", Instant::class.java).ge(from),
            DSL.field("h.ts", Instant::class.java).lt(to)
        )
        appName?.let { conditions += DSL.field("a.app_name", String::class.java).eq(it) }
        workerId?.let { conditions += DSL.field("h.worker_id", String::class.java).eq(it) }

        return dsl
            .select(method, uri, requestCount, averageDurationMs, p95DurationMs, maxDurationMs)
            .from(DSL.table("http_events").`as`("h"))
            .leftJoin(DSL.table("agents").`as`("a"))
            .on(DSL.field("a.worker_id").eq(DSL.field("h.worker_id")))
            .where(conditions)
            .groupBy(method, uri)
            .orderBy(p95DurationMs.desc(), method.asc(), uri.asc())
            .limit(limit)
            .fetch {
                PerformanceOverviewItem(
                    method = it.get(method)!!,
                    uri = it.get(uri)!!,
                    requestCount = it.get(requestCount)!!,
                    averageDurationMs = it.get(averageDurationMs)!!,
                    p95DurationMs = it.get(p95DurationMs)!!,
                    maxDurationMs = it.get(maxDurationMs)!!
                )
            }
    }
}
