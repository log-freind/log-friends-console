package com.logfriends.platform.domain.overview.business

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BusinessOverviewService(
    private val dsl: DSLContext
) {

    fun getOverview(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): List<BusinessEventCount> {
        val customEvents = DSL.table(DSL.name("custom_events")).`as`("c")
        val agents = DSL.table(DSL.name("agents")).`as`("a")
        val eventName = DSL.field(DSL.name("c", "event_name"), String::class.java)
        val eventCount = DSL.count().cast(Long::class.java)
        val conditions = mutableListOf(
            DSL.field(DSL.name("c", "ts"), Instant::class.java).ge(from),
            DSL.field(DSL.name("c", "ts"), Instant::class.java).lt(to)
        )

        workerId?.let {
            conditions.add(DSL.field(DSL.name("c", "worker_id"), String::class.java).eq(it))
        }
        appName?.let {
            conditions.add(DSL.field(DSL.name("a", "app_name"), String::class.java).eq(it))
        }

        val source = if (appName == null) {
            customEvents
        } else {
            customEvents.join(agents).on(
                DSL.field(DSL.name("a", "worker_id"), String::class.java)
                    .eq(DSL.field(DSL.name("c", "worker_id"), String::class.java))
            )
        }

        return dsl.select(
            eventName.`as`("eventName"),
            eventCount.`as`("eventCount")
        )
            .from(source)
            .where(conditions)
            .groupBy(eventName)
            .orderBy(eventCount.desc(), eventName.asc())
            .limit(limit)
            .fetch { record ->
                BusinessEventCount(
                    eventName = record.get("eventName", String::class.java),
                    eventCount = record.get("eventCount", Long::class.java)
                )
            }
    }
}

data class BusinessEventCount(
    val eventName: String,
    val eventCount: Long
)
