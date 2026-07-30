package com.logfriends.platform.overview.reliability

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ReliabilityOverviewRepository(
    private val dsl: DSLContext
) {
    fun getHttpSummary(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?
    ): HttpCounts {
        val totalRequests = DSL.count().cast(Long::class.java).`as`("total_requests")
        val errorRequests = DSL.count()
            .filterWhere(DSL.field("h.status_code", Int::class.java).ge(400))
            .cast(Long::class.java)
            .`as`("error_requests")

        val record = dsl.select(totalRequests, errorRequests)
            .from(DSL.table("http_events").`as`("h"))
            .where(httpConditions(from, to, appName, workerId))
            .fetchOne()!!

        return HttpCounts(
            totalRequests = record.get(totalRequests) ?: 0L,
            errorRequests = record.get(errorRequests) ?: 0L
        )
    }

    fun getFailedEventCount(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?
    ): Long {
        val failedEvents = DSL.count().cast(Long::class.java).`as`("failed_events")
        return dsl.select(failedEvents)
            .from(DSL.table("ingest_failed_events").`as`("f"))
            .where(ingestConditions(from, to, appName, workerId))
            .fetchOne(failedEvents) ?: 0L
    }

    fun getTopHttpErrors(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): List<TopHttpError> {
        val method = DSL.field("h.method", String::class.java)
        val uri = DSL.field("h.uri", String::class.java)
        val statusCode = DSL.field("h.status_code", Int::class.java)
        val errorCount = DSL.count().cast(Long::class.java).`as`("error_count")

        return dsl.select(method, uri, statusCode, errorCount)
            .from(DSL.table("http_events").`as`("h"))
            .where(httpConditions(from, to, appName, workerId))
            .and(statusCode.ge(400))
            .groupBy(method, uri, statusCode)
            .orderBy(errorCount.desc(), method.asc(), uri.asc(), statusCode.asc())
            .limit(limit)
            .fetch { record ->
                TopHttpError(
                    method = record.get(method).orEmpty(),
                    uri = record.get(uri).orEmpty(),
                    statusCode = record.get(statusCode) ?: 0,
                    errorCount = record.get(errorCount) ?: 0L
                )
            }
    }

    fun getTopIngestFailures(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): List<TopIngestFailure> {
        val reasonCode = DSL.field("f.reason_code", String::class.java)
        val failureCount = DSL.count().cast(Long::class.java).`as`("failure_count")

        return dsl.select(reasonCode, failureCount)
            .from(DSL.table("ingest_failed_events").`as`("f"))
            .where(ingestConditions(from, to, appName, workerId))
            .groupBy(reasonCode)
            .orderBy(failureCount.desc(), reasonCode.asc())
            .limit(limit)
            .fetch { record ->
                TopIngestFailure(
                    reasonCode = record.get(reasonCode).orEmpty(),
                    failureCount = record.get(failureCount) ?: 0L
                )
            }
    }

    private fun httpConditions(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?
    ): List<Condition> = conditions("h", "ts", from, to, appName, workerId)

    private fun ingestConditions(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?
    ): List<Condition> = conditions("f", "failed_at", from, to, appName, workerId)

    private fun conditions(
        alias: String,
        timestampColumn: String,
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?
    ): List<Condition> {
        val eventWorkerId = DSL.field("$alias.worker_id", String::class.java)
        val conditions = mutableListOf(
            DSL.field("$alias.$timestampColumn", Instant::class.java).ge(from),
            DSL.field("$alias.$timestampColumn", Instant::class.java).lt(to)
        )
        workerId?.let { conditions.add(eventWorkerId.eq(it)) }
        appName?.let {
            conditions.add(
                DSL.exists(
                    DSL.selectOne()
                        .from(DSL.table("agents").`as`("a"))
                        .where(DSL.field("a.worker_id", String::class.java).eq(eventWorkerId))
                        .and(DSL.field("a.app_name", String::class.java).eq(it))
                )
            )
        }
        return conditions
    }
}

data class HttpCounts(
    val totalRequests: Long,
    val errorRequests: Long
)
