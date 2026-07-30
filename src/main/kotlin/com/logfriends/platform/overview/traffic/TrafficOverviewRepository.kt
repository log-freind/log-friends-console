package com.logfriends.platform.overview.traffic

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class TrafficOverviewRepository(
    private val dsl: DSLContext
) {
    fun findTopTraffic(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): List<TrafficOverviewItem> {
        val httpEvents = DSL.table("http_events").`as`("h")
        val agents = DSL.table("agents").`as`("a")
        val method = DSL.field("h.method", String::class.java)
        val uri = DSL.field("h.uri", String::class.java)
        val requestCount = DSL.count().cast(Long::class.java).`as`("request_count")
        val conditions = mutableListOf(
            DSL.field("h.ts", Instant::class.java).ge(from),
            DSL.field("h.ts", Instant::class.java).lt(to)
        )
        workerId?.let { conditions.add(DSL.field("h.worker_id", String::class.java).eq(it)) }

        val select = dsl.select(method, uri, requestCount).from(httpEvents)
        val filtered = if (appName != null) {
            select.join(agents)
                .on(DSL.field("a.worker_id", String::class.java).eq(DSL.field("h.worker_id", String::class.java)))
                .where(conditions + DSL.field("a.app_name", String::class.java).eq(appName))
        } else {
            select.where(conditions)
        }

        return filtered
            .groupBy(method, uri)
            .orderBy(requestCount.desc(), method.asc(), uri.asc())
            .limit(limit)
            .fetch { record ->
                TrafficOverviewItem(
                    method = record.get(method),
                    uri = record.get(uri),
                    requestCount = record.get(requestCount)
                )
            }
    }
}
