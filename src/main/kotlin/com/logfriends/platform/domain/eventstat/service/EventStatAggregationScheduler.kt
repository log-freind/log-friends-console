package com.logfriends.platform.domain.eventstat.service

import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.eventstat.entity.EventType
import org.jooq.DSLContext
import org.jooq.Record
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class EventStatAggregationScheduler(
    private val dsl: DSLContext,
    private val agentRepository: AgentRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${logfriends.stats-scheduler.fixed-delay-ms:60000}")
    fun runScheduledAggregation() {
        try {
            val result = aggregateRecent()
            log.debug(
                "[EventStatScheduler] eventStatsUpserted={} failureStatsUpserted={} skippedWorkers={}",
                result.eventStatsUpserted,
                result.failureStatsUpserted,
                result.skippedWorkers
            )
        } catch (ex: Exception) {
            log.error("[EventStatScheduler] aggregation failed", ex)
        }
    }

    @Transactional
    fun aggregateRecent(now: Instant = Instant.now(clock)): EventStatAggregationResult {
        val to = now.truncatedTo(ChronoUnit.MINUTES)
        val from = to.minus(5, ChronoUnit.MINUTES)

        var eventStatsUpserted = 0
        val skippedWorkers = linkedSetOf<String>()

        rawEventAggregationSpecs.forEach { spec ->
            fetchEventStats(spec, from, to).forEach { stat ->
                val agent = agentRepository.findByWorkerId(stat.workerId).orElse(null)
                if (agent?.id == null) {
                    skippedWorkers += stat.workerId
                    return@forEach
                }

                upsertEventStat(
                    agentId = agent.id!!,
                    eventType = spec.eventType,
                    windowStart = stat.windowStart,
                    count = stat.count,
                    errorCount = stat.errorCount,
                    avgDurationMs = stat.avgDurationMs
                )
                eventStatsUpserted++
            }
        }

        val failureStatsUpserted = fetchFailureStats(from, to).sumOf { stat ->
            upsertIngestFailureStat(stat)
            1
        }

        return EventStatAggregationResult(
            eventStatsUpserted = eventStatsUpserted,
            failureStatsUpserted = failureStatsUpserted,
            skippedWorkers = skippedWorkers.toList()
        )
    }

    private fun fetchEventStats(
        spec: RawEventAggregationSpec,
        from: Instant,
        to: Instant
    ): List<RawEventStatRow> =
        dsl.resultQuery(
            """
            SELECT
                worker_id,
                date_trunc('minute', ${spec.timestampColumn}) AS window_start,
                COUNT(*)::bigint AS count,
                SUM(CASE WHEN ${spec.errorConditionSql} THEN 1 ELSE 0 END)::bigint AS error_count,
                ${spec.avgDurationSql} AS avg_duration_ms
            FROM ${spec.tableName}
            WHERE ${spec.timestampColumn} >= ? AND ${spec.timestampColumn} < ?
            GROUP BY worker_id, window_start
            """.trimIndent(),
            Timestamp.from(from),
            Timestamp.from(to)
        ).fetch { record ->
            RawEventStatRow(
                workerId = record.get("worker_id", String::class.java),
                windowStart = record.instant("window_start"),
                count = record.longValue("count"),
                errorCount = record.longValue("error_count"),
                avgDurationMs = record.doubleOrNull("avg_duration_ms")
            )
        }

    private fun upsertEventStat(
        agentId: Long,
        eventType: EventType,
        windowStart: Instant,
        count: Long,
        errorCount: Long,
        avgDurationMs: Double?
    ) {
        dsl.query(
            """
            INSERT INTO event_stats (
                agent_id, event_type, window_start, count, error_count, avg_duration_ms, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (agent_id, event_type, window_start)
            DO UPDATE SET
                count = EXCLUDED.count,
                error_count = EXCLUDED.error_count,
                avg_duration_ms = EXCLUDED.avg_duration_ms,
                updated_at = now()
            """.trimIndent(),
            agentId,
            eventType.name,
            Timestamp.from(windowStart),
            count,
            errorCount,
            avgDurationMs
        ).execute()
    }

    private fun fetchFailureStats(from: Instant, to: Instant): List<IngestFailureStatRow> =
        dsl.resultQuery(
            """
            SELECT
                normalized_worker_id AS worker_id,
                reason_code,
                window_start,
                COUNT(*)::bigint AS count
            FROM (
                SELECT
                    COALESCE(NULLIF(worker_id, ''), ?) AS normalized_worker_id,
                    reason_code,
                    date_trunc('minute', failed_at) AS window_start
                FROM ingest_failed_events
                WHERE failed_at >= ? AND failed_at < ?
            ) failures
            GROUP BY normalized_worker_id, reason_code, window_start
            """.trimIndent(),
            UNKNOWN_WORKER_ID,
            Timestamp.from(from),
            Timestamp.from(to)
        ).fetch { record ->
            IngestFailureStatRow(
                workerId = record.get("worker_id", String::class.java),
                reasonCode = record.get("reason_code", String::class.java),
                windowStart = record.instant("window_start"),
                count = record.longValue("count")
            )
        }

    private fun upsertIngestFailureStat(stat: IngestFailureStatRow) {
        dsl.query(
            """
            INSERT INTO ingest_failure_stats (
                worker_id, reason_code, window_start, count, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, now(), now())
            ON CONFLICT (worker_id, reason_code, window_start)
            DO UPDATE SET
                count = EXCLUDED.count,
                updated_at = now()
            """.trimIndent(),
            stat.workerId,
            stat.reasonCode,
            Timestamp.from(stat.windowStart),
            stat.count
        ).execute()
    }

    private fun Record.instant(fieldName: String): Instant {
        val value = get(fieldName)
        return when (value) {
            is Instant -> value
            is OffsetDateTime -> value.toInstant()
            else -> error("Unsupported timestamp value for $fieldName: ${value?.javaClass?.name}")
        }
    }

    private fun Record.longValue(fieldName: String): Long {
        val value = get(fieldName)
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is BigDecimal -> value.toLong()
            else -> error("Unsupported long value for $fieldName: ${value?.javaClass?.name}")
        }
    }

    private fun Record.doubleOrNull(fieldName: String): Double? {
        val value = get(fieldName) ?: return null
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is BigDecimal -> value.toDouble()
            is Number -> value.toDouble()
            else -> error("Unsupported double value for $fieldName: ${value.javaClass.name}")
        }
    }

    companion object {
        const val UNKNOWN_WORKER_ID = "UNKNOWN_WORKER"

        private val rawEventAggregationSpecs = listOf(
            RawEventAggregationSpec(
                tableName = "http_events",
                timestampColumn = "ts",
                eventType = EventType.HTTP,
                errorConditionSql = "status_code >= 500 OR exception_stack <> ''",
                avgDurationSql = "AVG(duration_ms)::double precision"
            ),
            RawEventAggregationSpec(
                tableName = "logs",
                timestampColumn = "ts",
                eventType = EventType.LOG,
                errorConditionSql = "level = 'ERROR' OR exception <> '' OR exception_stack <> ''",
                avgDurationSql = "NULL::double precision"
            ),
            RawEventAggregationSpec(
                tableName = "jdbc_events",
                timestampColumn = "ts",
                eventType = EventType.JDBC,
                errorConditionSql = "exception <> '' OR exception_stack <> ''",
                avgDurationSql = "AVG(duration_ms)::double precision"
            ),
            RawEventAggregationSpec(
                tableName = "method_traces",
                timestampColumn = "ts",
                eventType = EventType.METHOD_TRACE,
                errorConditionSql = "exception <> '' OR exception_stack <> ''",
                avgDurationSql = "AVG(duration_ms)::double precision"
            ),
            RawEventAggregationSpec(
                tableName = "custom_events",
                timestampColumn = "ts",
                eventType = EventType.LOG_EVENT,
                errorConditionSql = "FALSE",
                avgDurationSql = "NULL::double precision"
            )
        )
    }
}

data class EventStatAggregationResult(
    val eventStatsUpserted: Int,
    val failureStatsUpserted: Int,
    val skippedWorkers: List<String>
)

private data class RawEventAggregationSpec(
    val tableName: String,
    val timestampColumn: String,
    val eventType: EventType,
    val errorConditionSql: String,
    val avgDurationSql: String
)

private data class RawEventStatRow(
    val workerId: String,
    val windowStart: Instant,
    val count: Long,
    val errorCount: Long,
    val avgDurationMs: Double?
)

private data class IngestFailureStatRow(
    val workerId: String,
    val reasonCode: String,
    val windowStart: Instant,
    val count: Long
)
