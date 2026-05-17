package com.logfriends.platform.infrastructure.query

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * SDK가 /ingest 로 전송한 원시 이벤트 조회
 * PostgreSQL Raw Event 테이블 조회.
 */
@Service
class EventQueryService(
    private val dsl: DSLContext
) {

    /** HTTP 이벤트 조회 */
    fun queryHttpEvents(
        workerId: String?,
        from: Instant,
        to: Instant
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("ts").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }

        return dsl.select()
            .from(DSL.table("http_events"))
            .where(conditions)
            .orderBy(DSL.field("ts").asc(), DSL.field("id").asc())
            .fetchMaps()
    }

    /** LOG 이벤트 조회 */
    fun queryLogEvents(
        workerId: String?,
        level: String?,
        from: Instant,
        to: Instant
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("ts").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        level?.let { conditions.add(DSL.field("level").eq(it)) }

        return dsl.select()
            .from(DSL.table("logs"))
            .where(conditions)
            .orderBy(DSL.field("ts").asc(), DSL.field("id").asc())
            .fetchMaps()
    }

    /** JDBC 이벤트 조회 */
    fun queryJdbcEvents(
        workerId: String?,
        from: Instant,
        to: Instant,
        minDurationMs: Long? = null
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("ts").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        minDurationMs?.let { conditions.add(DSL.field("duration_ms").ge(it)) }

        return dsl.select()
            .from(DSL.table("jdbc_events"))
            .where(conditions)
            .orderBy(DSL.field("ts").asc(), DSL.field("id").asc())
            .fetchMaps()
    }

    /** @LogEvent 커스텀 이벤트 조회 */
    fun queryCustomEvents(
        workerId: String?,
        eventName: String?,
        from: Instant,
        to: Instant
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("ts").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        eventName?.let { conditions.add(DSL.field("event_name").eq(it)) }

        return dsl.select()
            .from(DSL.table("custom_events"))
            .where(conditions)
            .orderBy(DSL.field("ts").asc(), DSL.field("id").asc())
            .fetchMaps()
    }

    /** METHOD_TRACE 이벤트 조회 */
    fun queryMethodTraceEvents(
        workerId: String?,
        className: String?,
        from: Instant,
        to: Instant,
        minDurationMs: Long? = null
    ): List<Map<String, Any?>> {
        val conditions = mutableListOf(
            DSL.field("ts").between(from, to)
        )
        workerId?.let { conditions.add(DSL.field("worker_id").eq(it)) }
        className?.let { conditions.add(DSL.field("class_name").eq(it)) }
        minDurationMs?.let { conditions.add(DSL.field("duration_ms").ge(it)) }

        return dsl.select()
            .from(DSL.table("method_traces"))
            .where(conditions)
            .orderBy(DSL.field("ts").asc(), DSL.field("id").asc())
            .fetchMaps()
    }
}
