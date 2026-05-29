package com.logfriends.platform.api.rest

import com.logfriends.platform.api.dto.AgentRegisterRequest
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.entity.AgentStatus
import com.logfriends.platform.domain.agent.service.AgentService
import com.logfriends.platform.domain.logspec.entity.LogSpecSnapshot
import com.logfriends.platform.domain.logspec.service.LogSpecService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.test.util.ReflectionTestUtils

class AgentControllerTest {

    private val agentService: AgentService = mock()
    private val logSpecService: LogSpecService = mock()
    private val controller = AgentController(agentService, logSpecService)

    @Test
    fun `registerAgent returns appName based known LogSpecs for SDK handshake`() {
        val agent = Agent(
            workerId = "order-service-local-1",
            appName = "order-service",
            metadata = mapOf("env" to "local"),
            status = AgentStatus.RUNNING
        )
        ReflectionTestUtils.setField(agent, "id", 1L)

        val orderCreated = logSpec("orderCreated")
        val orderCancelled = logSpec("orderCancelled")
        val duplicatedOrderCreated = logSpec("orderCreated")

        given(
            agentService.register(
                workerId = "order-service-local-1",
                appName = "order-service",
                sdkVersion = "0.1.0",
                javaVersion = "21",
                hostname = "local",
                metadata = mapOf("env" to "local")
            )
        ).willReturn(agent)
        given(logSpecService.findAllByAppName("order-service"))
            .willReturn(listOf(orderCreated, orderCancelled, duplicatedOrderCreated))

        val response = controller.registerAgent(
            AgentRegisterRequest(
                workerId = "order-service-local-1",
                appName = "order-service",
                sdkVersion = "0.1.0",
                javaVersion = "21",
                hostname = "local",
                metadata = mapOf("env" to "local")
            )
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body!!.agentId).isEqualTo(1L)
        assertThat(response.body!!.workerId).isEqualTo("order-service-local-1")
        assertThat(response.body!!.appName).isEqualTo("order-service")
        assertThat(response.body!!.knownLogSpecs.map { it.eventName })
            .containsExactly("orderCancelled", "orderCreated")
    }

    private fun logSpec(eventName: String): LogSpecSnapshot {
        val spec = LogSpecSnapshot(
            agentId = 1L,
            specName = eventName,
            description = "$eventName description"
        )
        ReflectionTestUtils.setField(spec, "id", eventName.hashCode().toLong())
        return spec
    }
}
