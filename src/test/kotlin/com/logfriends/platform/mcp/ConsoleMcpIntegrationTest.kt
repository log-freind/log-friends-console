package com.logfriends.platform.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.logfriends.platform.api.dto.*
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.service.AgentService
import com.logfriends.platform.domain.logcatalog.service.LogCatalogService
import com.logfriends.platform.infrastructure.query.EventQueryService
import com.logfriends.platform.overview.reliability.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = [ConsoleMcpIntegrationTest.App::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["logfriends.mcp.enabled=true"])
class ConsoleMcpIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class])
    @Import(ConsoleMcpConfiguration::class, ConsoleMcpTools::class)
    class App

    @LocalServerPort private var port = 0
    @Autowired private lateinit var mapper: ObjectMapper
    @MockitoBean private lateinit var catalog: LogCatalogService
    @MockitoBean private lateinit var events: EventQueryService
    @MockitoBean private lateinit var agents: AgentService
    @MockitoBean private lateinit var reliability: ReliabilityOverviewService
    private val client = HttpClient.newHttpClient()

    private fun post(method: String, params: Any = emptyMap<String, Any>(), origin: String? = null, id: Int? = 1): HttpResponse<String> {
        val body = mutableMapOf<String, Any>("jsonrpc" to "2.0", "method" to method, "params" to params)
        id?.let { body["id"] = it }
        val request = HttpRequest.newBuilder(URI("http://localhost:$port/mcp"))
            .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2025-06-18")
        origin?.let { request.header("Origin", it) }
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun call(name: String, args: Map<String, Any> = emptyMap()): JsonNode {
        val response = post("tools/call", mapOf("name" to name, "arguments" to args))
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200)
        return mapper.readTree(response.body())
    }

    @Test fun `initialize negotiate capabilities and accept initialized notification`() {
        val response = post("initialize", mapOf("protocolVersion" to "2025-06-18", "capabilities" to emptyMap<String, Any>(), "clientInfo" to mapOf("name" to "test", "version" to "1")))
        assertThat(response.statusCode()).isEqualTo(200)
        val result = mapper.readTree(response.body())["result"]
        assertThat(result["serverInfo"]["name"].asText()).isEqualTo("log-friends-console")
        assertThat(result.has("capabilities")).isTrue()
        assertThat(post("notifications/initialized", id = null).statusCode()).isEqualTo(202)
    }

    @Test fun `advertises only four read only tools`() {
        val response = post("tools/list")
        val tools = mapper.readTree(response.body())["result"]["tools"]
        assertThat(tools.map { it["name"].asText() }).containsExactlyInAnyOrder("search_log_catalog", "get_event_samples", "get_ingest_failure_summary", "get_worker_status")
        tools.forEach { assertThat(it["annotations"]["readOnlyHint"].asBoolean()).isTrue() }
    }

    @Test fun `samples use bounded query and recursively mask secrets`() {
        val from = Instant.parse("2026-09-18T00:00:00Z")
        val to = from.plusSeconds(3600)
        `when`(events.queryCustomEvents("shop", null, "paid", from, to, 2)).thenReturn(listOf(
            mapOf("eventName" to "paid", "payload" to """{"email":"private@example.com","nested":[{"api_key":"secret-value"}],"amount":100}"""),
            mapOf("eventName" to "paid", "payload" to "{}")
        ))
        val result = call("get_event_samples", mapOf("appName" to "shop", "eventName" to "paid", "from" to from.toString(), "to" to to.toString(), "limit" to 1))["result"]
        assertThat(result["isError"].asBoolean()).isFalse()
        val data = result["structuredContent"]
        assertThat(data["truncated"].asBoolean()).isTrue()
        assertThat(data["returnedCount"].asInt()).isEqualTo(1)
        assertThat(data["items"][0]["payload"]["email"].asText()).isEqualTo("***")
        assertThat(result.toString()).doesNotContain("private@example.com", "secret-value")
    }

    @Test fun `rejects excessive ranges missing fields unknown arguments and invalid limits before querying`() {
        val cases = listOf(
            mapOf("appName" to "shop"),
            mapOf("appName" to "shop", "eventName" to "paid", "limit" to 21),
            mapOf("appName" to "shop", "eventName" to "paid", "limit" to 1.5),
            mapOf("appName" to "shop", "eventName" to "paid", "sql" to "select 1"),
            mapOf("appName" to "shop", "eventName" to "paid", "from" to "2026-01-01T00:00:00Z", "to" to "2026-02-01T00:00:00Z")
        )
        cases.forEach { args ->
            val response = call("get_event_samples", args)
            assertThat(response.has("error") || response.path("result").path("isError").asBoolean()).isTrue()
        }
        verifyNoInteractions(events)
    }

    @Test fun `worker response excludes arbitrary metadata`() {
        `when`(agents.findAll()).thenReturn(listOf(Agent("worker-1", "shop", metadata = mapOf("secret" to "do-not-return"))))
        val result = call("get_worker_status")["result"]
        assertThat(result["structuredContent"]["items"][0]["workerId"].asText()).isEqualTo("worker-1")
        assertThat(result.toString()).doesNotContain("do-not-return", "metadata")
    }

    @Test fun `failure summary excludes application errors and raw payloads`() {
        val from = Instant.parse("2026-09-18T00:00:00Z")
        val to = from.plusSeconds(3600)
        `when`(reliability.getOverview(from, to, null, null, 21)).thenReturn(ReliabilityOverviewResponse(
            HttpReliabilitySummary(5, 1, 0.2), IngestReliabilitySummary(3), emptyList(), listOf(TopIngestFailure("INVALID_PAYLOAD", 3))))
        val result = call("get_ingest_failure_summary", mapOf("from" to from.toString(), "to" to to.toString()))["result"]
        assertThat(result["structuredContent"]["items"][0]["failureCount"].asInt()).isEqualTo(3)
        assertThat(result.toString()).doesNotContain("errorRate", "totalRequests")
    }

    @Test fun `catalog discovers apps and bounds results`() {
        `when`(catalog.listApps()).thenReturn(LogCatalogAppsResponse(listOf(LogCatalogAppResponse("a", listOf("w1")), LogCatalogAppResponse("b", listOf("w2")))))
        val data = call("search_log_catalog", mapOf("limit" to 1))["result"]["structuredContent"]
        assertThat(data["returnedCount"].asInt()).isEqualTo(1)
        assertThat(data["truncated"].asBoolean()).isTrue()
    }

    @Test fun `large individual records are omitted with explicit truncation`() {
        `when`(agents.findAll()).thenReturn(listOf(Agent("x".repeat(70000), "shop")))
        val result = call("get_worker_status")["result"]
        assertThat(result["structuredContent"]["truncated"].asBoolean()).isTrue()
        assertThat(result["structuredContent"]["returnedCount"].asInt()).isZero()
    }

    @Test fun `database errors do not expose internal exception details and next call succeeds`() {
        `when`(agents.findAll()).thenThrow(RuntimeException("db-password=private"))
            .thenReturn(emptyList())
        val failed = call("get_worker_status")
        assertThat(failed["result"]["isError"].asBoolean()).isTrue()
        assertThat(failed.toString()).doesNotContain("db-password", "private")
        assertThat(call("get_worker_status")["result"]["isError"].asBoolean()).isFalse()
    }

    @Test fun `untrusted origin is forbidden`() {
        assertThat(post("tools/list", origin = "https://attacker.example").statusCode()).isEqualTo(403)
    }

    @Test fun `unknown tool returns protocol error`() {
        val result = call("delete_log_spec")
        assertThat(result.has("error")).isTrue()
    }

    @Test fun `official MCP client initializes discovers and calls over HTTP`() {
        `when`(agents.findAll()).thenReturn(emptyList())
        val transport = HttpClientStreamableHttpTransport.builder("http://localhost:$port").endpoint("/mcp").build()
        McpClient.sync(transport).build().use { mcp ->
            assertThat(mcp.initialize().serverInfo().name()).isEqualTo("log-friends-console")
            assertThat(mcp.listTools().tools()).hasSize(4)
            val result = mcp.callTool(McpSchema.CallToolRequest("get_worker_status", emptyMap()))
            assertThat(result.isError).isFalse()
        }
    }

    @Test fun `MCP is absent unless explicitly enabled`() {
        ApplicationContextRunner().withUserConfiguration(ConsoleMcpConfiguration::class.java).run { context ->
            assertThat(context.containsBean("mcpRouter")).isFalse()
            assertThat(context.containsBean("mcpServer")).isFalse()
        }
    }

    @Test fun `catalog filters event names without returning examples or samples`() {
        val entry = LogCatalogEventResponse("orderPaid", "payment", null, LogCatalogSpecStatus.REGISTERED,
            emptyList(), emptyList(), listOf(LogCatalogSampleResponse("w", Instant.now(), mapOf("value" to "private-sample"))), emptyList(), emptyList())
        `when`(catalog.listEvents("shop", null, 1)).thenReturn(LogCatalogEventsResponse("shop", null, listOf("w"), emptyList(), emptyList(), listOf(entry, entry.copy(eventName = "userCreated"))))
        val result = call("search_log_catalog", mapOf("appName" to "shop", "eventName" to "paid"))["result"]
        assertThat(result["structuredContent"]["items"].size()).isEqualTo(1)
        assertThat(result.toString()).doesNotContain("private-sample", "userCreated")
    }

    @Test fun `excess concurrent queries fail immediately and capacity recovers`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        `when`(agents.findAll()).thenAnswer {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            emptyList<Agent>()
        }
        val first = CompletableFuture.supplyAsync { call("get_worker_status") }
        val second = CompletableFuture.supplyAsync { call("get_worker_status") }
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            val rejected = call("get_worker_status")["result"]
            assertThat(rejected["isError"].asBoolean()).isTrue()
            assertThat(rejected.toString()).contains("capacity is busy")
        } finally {
            release.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
        }
        assertThat(call("get_worker_status")["result"]["isError"].asBoolean()).isFalse()
    }
}
