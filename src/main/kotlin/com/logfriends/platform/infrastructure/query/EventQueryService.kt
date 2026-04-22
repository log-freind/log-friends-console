package com.logfriends.platform.infrastructure.query

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * SDK가 /ingest 로 전송한 원시 이벤트 조회
 * PostgreSQL (log_events, http_events 등 테이블)
 */
@Service
class EventQueryService(
    private val dsl: DSLContext
) {

    /** HTTP 이벤트 조회 */
    fun queryHttpEvents(
        workerId: String?,
        from: Instant,
        to: Instant,
        limit: Int = 100
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("timestamp").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }

        return dsl.select()
            .from(DSL.table("http_events"))
            .where(conditions)
            .orderBy(DSL.field("timestamp").desc())
            .limit(limit)
            .fetchMaps()
    }

    /** LOG 이벤트 조회 */
    fun queryLogEvents(
        workerId: String?,
        level: String?,
        from: Instant,
        to: Instant,
        limit: Int = 100
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("timestamp").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        level?.let { conditions.add(DSL.field("level").eq(it)) }

        return dsl.select()
            .from(DSL.table("log_events"))
            .where(conditions)
            .orderBy(DSL.field("timestamp").desc())
            .limit(limit)
            .fetchMaps()
    }

    /** JDBC 이벤트 조회 */
    fun queryJdbcEvents(
        workerId: String?,
        from: Instant,
        to: Instant,
        minDurationMs: Long? = null,
        limit: Int = 100
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("timestamp").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        minDurationMs?.let { conditions.add(DSL.field("duration_ms").ge(it)) }

        return dsl.select()
            .from(DSL.table("jdbc_events"))
            .where(conditions)
            .orderBy(DSL.field("duration_ms").desc())
            .limit(limit)
            .fetchMaps()
    }

    /** @LogEvent 커스텀 이벤트 조회 */
    fun queryCustomEvents(
        workerId: String?,
        eventName: String?,
        from: Instant,
        to: Instant,
        limit: Int = 100
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("timestamp").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        eventName?.let { conditions.add(DSL.field("event_name").eq(it)) }

        return dsl.select()
            .from(DSL.table("custom_events"))
            .where(conditions)
            .orderBy(DSL.field("timestamp").desc())
            .limit(limit)
            .fetchMaps()
    }

    /** METHOD_TRACE 이벤트 조회 */
    fun queryMethodTraceEvents(
        workerId: String?,
        className: String?,
        from: Instant,
        to: Instant,
        minDurationMs: Long? = null,
        limit: Int = 100
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("timestamp").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        className?.let { conditions.add(DSL.field("class_name").eq(it)) }
        minDurationMs?.let { conditions.add(DSL.field("duration_ms").ge(it)) }

        return dsl.select()
            .from(DSL.table("method_trace_events"))
            .where(conditions)
            .orderBy(DSL.field("duration_ms").desc())
            .limit(limit)
            .fetchMaps()
    }
}
