package com.logfriends.platform.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.logfriends.platform.domain.agent.service.AgentService
import com.logfriends.platform.domain.logcatalog.service.LogCatalogService
import com.logfriends.platform.infrastructure.query.EventQueryService
import com.logfriends.platform.overview.reliability.ReliabilityOverviewService
import io.modelcontextprotocol.json.McpJsonDefaults
import io.modelcontextprotocol.server.McpStatelessServerFeatures
import io.modelcontextprotocol.spec.McpSchema
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Semaphore

@Component
class ConsoleMcpTools(
    private val catalog: LogCatalogService,
    private val events: EventQueryService,
    private val reliability: ReliabilityOverviewService,
    private val agents: AgentService,
    private val mapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    // Leave shared DB capacity available for ingest; do not queue MCP callers.
    private val querySlots = Semaphore(2)

    fun specifications(): List<McpStatelessServerFeatures.SyncToolSpecification> = listOf(
        tool("search_log_catalog", "List apps when appName is omitted; otherwise search event contracts and discovered hints. No payloads or field examples are returned.",
            listOf("appName", "workerId", "eventName")),
        tool("get_event_samples", "Read recent masked LOG_EVENT samples for one app and event. Default past 24 hours; maximum 7 days. Default limit 5, maximum 20.",
            listOf("appName", "workerId", "eventName", "from", "to"), listOf("appName", "eventName"), 5, 20),
        tool("get_ingest_failure_summary", "Summarize ingest failure counts, not application errors or failed payloads. Default past 24 hours; maximum 7 days.",
            listOf("appName", "workerId", "from", "to")),
        tool("get_worker_status", "Read registered Worker heartbeat status. This does not report SDK queue or JVM heap health. Arbitrary Agent metadata is excluded.",
            listOf("appName", "workerId"))
    )

    private fun tool(name: String, description: String, strings: List<String>, required: List<String> = emptyList(), defaultLimit: Int = 20, maxLimit: Int = 50): McpStatelessServerFeatures.SyncToolSpecification {
        val properties = strings.associateWith { key ->
            if (key == "from" || key == "to") mapOf("type" to "string", "format" to "date-time")
            else mapOf("type" to "string", "minLength" to 1, "maxLength" to 200)
        } + ("limit" to mapOf("type" to "integer", "minimum" to 1, "maximum" to maxLimit, "default" to defaultLimit))
        val schema = mapper.writeValueAsString(mapOf("type" to "object", "properties" to properties, "required" to required, "additionalProperties" to false))
        val definition = McpSchema.Tool.builder().name(name).description(description)
            .inputSchema(McpJsonDefaults.getMapper(), schema)
            .annotations(McpSchema.ToolAnnotations(name, true, false, true, false, false)).build()
        return McpStatelessServerFeatures.SyncToolSpecification(definition) { _, request ->
            execute(name, request.arguments() ?: emptyMap(), strings.toSet() + "limit", required, defaultLimit, maxLimit)
        }
    }

    internal fun execute(name: String, args: Map<String, Any>, allowed: Set<String>, required: List<String>, defaultLimit: Int, maxLimit: Int): McpSchema.CallToolResult {
        if (!querySlots.tryAcquire()) return error("MCP query capacity is busy. Retry later.")
        try {
            require(args.keys.all { it in allowed }) { "Unknown argument" }
            val strings = args.filterKeys { it != "limit" }.mapValues { (_, value) ->
                require(value is String && value.isNotBlank() && value.length <= 200) { "Arguments must be nonblank strings of at most 200 characters" }
                value.trim()
            }
            require(required.all { strings.containsKey(it) }) { "Required arguments: ${required.joinToString()}" }
            val rawLimit = args["limit"]
            val limit = if (rawLimit == null) defaultLimit else {
                require(rawLimit is Number && rawLimit.toDouble() == rawLimit.toInt().toDouble()) { "limit must be an integer" }
                rawLimit.toInt().also { require(it in 1..maxLimit) { "limit must be between 1 and $maxLimit" } }
            }
            val app = strings["appName"]
            val worker = strings["workerId"]
            val event = strings["eventName"]
            var from: Instant? = null
            var to: Instant? = null
            if (name in setOf("get_event_samples", "get_ingest_failure_summary")) {
                to = strings["to"]?.let { Instant.parse(it) } ?: Instant.now()
                from = strings["from"]?.let { Instant.parse(it) } ?: to.minusSeconds(86400)
                require(from < to && Duration.between(from, to) <= Duration.ofDays(7)) { "Time range must be positive and at most 7 days" }
            }
            val rows: List<Any> = when (name) {
                "search_log_catalog" -> if (app == null) {
                    require(worker == null && event == null) { "appName is required with workerId or eventName" }
                    catalog.listApps().apps.map { mapOf("appName" to it.appName, "workerCount" to it.workerIds.size) }
                } else {
                    catalog.listEvents(app, worker, 1).events.filter { event == null || it.eventName.contains(event, true) }.map { item ->
                        mapOf("eventName" to item.eventName, "description" to item.description,
                            "apiContext" to item.apiContext, "specStatus" to item.specStatus,
                            "fields" to item.fields.map { mapOf("name" to it.name, "type" to it.type, "required" to it.required, "description" to it.description) },
                            "discoveredSources" to item.discoveredHints.map { mapOf("sourceClass" to it.sourceClass, "sourceMethod" to it.sourceMethod) },
                            "mismatches" to item.mismatches)
                    }
                }
                "get_event_samples" -> events.queryCustomEvents(app, worker, event, from!!, to!!, limit + 1).map { row ->
                    mapOf("eventName" to row["eventName"], "workerId" to row["workerId"], "timestamp" to row["timestamp"],
                        "payload" to row["payload"]?.let { value ->
                            if (value is Map<*, *>) mapper.valueToTree<JsonNode>(value) else mapper.readTree(value.toString())
                        })
                }
                "get_ingest_failure_summary" -> reliability.getOverview(from!!, to!!, app, worker, limit + 1).topIngestFailures
                "get_worker_status" -> (worker?.let { listOf(agents.findByWorkerId(it)) } ?: agents.findAll())
                    .filter { app == null || it.appName == app }.sortedBy { it.workerId }.map {
                        mapOf("agentId" to it.id, "workerId" to it.workerId, "appName" to it.appName,
                            "status" to it.status, "sourceType" to it.sourceType, "lastHeartbeat" to it.lastHeartbeat)
                    }
                else -> throw IllegalArgumentException("Unknown tool")
            }
            val selected = rows.take(limit).map { redact(mapper.valueToTree(it)) }.toMutableList()
            var truncated = rows.size > limit
            fun envelope() = mapOf("source" to "log-friends-console/$name", "filters" to strings,
                "timeRange" to mapOf("from" to from, "to" to to), "observedAt" to Instant.now(),
                "returnedCount" to selected.size, "truncated" to truncated, "items" to selected)
            while (mapper.writeValueAsBytes(envelope()).size > 64 * 1024 && selected.isNotEmpty()) {
                selected.removeAt(selected.lastIndex)
                truncated = true
            }
            // Normalize Java time and DTO values before the SDK serializes the protocol envelope.
            val result = mapper.convertValue(envelope(), Map::class.java)
            return McpSchema.CallToolResult.builder().addTextContent(mapper.writeValueAsString(result)).structuredContent(result).isError(false).build()
        } catch (e: IllegalArgumentException) {
            return error(e.message ?: "Invalid arguments")
        } catch (e: java.time.format.DateTimeParseException) {
            return error("from and to must be ISO-8601 timestamps with timezone")
        } catch (e: com.logfriends.platform.common.exception.BusinessException) {
            return error("Requested app or worker was not found, or query parameters are invalid")
        } catch (e: Exception) {
            logger.warn("MCP query {} failed ({})", name, e.javaClass.simpleName)
            return error("Query failed. Check Console availability and retry with a smaller range.")
        } finally {
            querySlots.release()
        }
    }

    private fun error(message: String) = McpSchema.CallToolResult.builder().addTextContent(message).isError(true).build()

    private fun redact(node: JsonNode): JsonNode {
        if (node.isObject) {
            val result = mapper.createObjectNode()
            node.properties().forEach { (key, value) ->
                if (MASK_KEYS.any { key.lowercase().contains(it) }) result.put(key, "***")
                else result.set<JsonNode>(key, redact(value))
            }
            return result
        }
        if (node.isArray) return mapper.createArrayNode().also { array -> node.forEach { array.add(redact(it)) } }
        return node
    }

    companion object {
        private val MASK_KEYS = listOf("password", "passwd", "token", "secret", "authorization", "email", "phone", "ssn", "resident", "cookie", "apikey", "api_key")
    }
}
