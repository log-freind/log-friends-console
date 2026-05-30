package com.logfriends.platform.domain.discoveredlogevent.service

import com.logfriends.platform.api.dto.DiscoveredLogEventItemRequest
import com.logfriends.platform.api.dto.DiscoveredLogEventReportRequest
import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.repository.AgentRepository
import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEvent
import com.logfriends.platform.domain.discoveredlogevent.entity.DiscoveredLogEventStatus
import com.logfriends.platform.domain.discoveredlogevent.repository.DiscoveredLogEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DiscoveredLogEventServiceTest {

    private val agentRepository: AgentRepository = mock()
    private val discoveredLogEventRepository: DiscoveredLogEventRepository = mock()
    private val service = DiscoveredLogEventService(agentRepository, discoveredLogEventRepository)

    @Test
    fun `report creates discovered log event candidates`() {
        val agent = agent(id = 1L)
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent))
        given(
            discoveredLogEventRepository.findByAgentIdAndEventNameAndSourceClassAndSourceMethod(
                1L,
                "orderCreated",
                "com.example.OrderService",
                "createOrder"
            )
        ).willReturn(Optional.empty())
        given(discoveredLogEventRepository.save(any(DiscoveredLogEvent::class.java))).willAnswer { it.arguments[0] }

        val response = service.report(
            agentId = 1L,
            request = DiscoveredLogEventReportRequest(
                workerId = "order-service-local-1",
                appName = "order-service",
                appVersion = "0.1.0",
                events = listOf(
                    DiscoveredLogEventItemRequest(
                        eventName = "orderCreated",
                        sourceClass = "com.example.OrderService",
                        sourceMethod = "createOrder",
                        parameterNames = listOf("request")
                    )
                )
            )
        )

        assertThat(response.received).isEqualTo(1)
        assertThat(response.upserted).isEqualTo(1)
        verify(discoveredLogEventRepository).save(any(DiscoveredLogEvent::class.java))
    }

    @Test
    fun `report refreshes existing candidate without resetting ignored status`() {
        val agent = agent(id = 1L)
        val existing = DiscoveredLogEvent(
            agent = agent,
            eventName = "orderCreated",
            sourceClass = "com.example.OrderService",
            sourceMethod = "createOrder",
            parameterNames = listOf("oldRequest"),
            status = DiscoveredLogEventStatus.IGNORED
        )
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent))
        given(
            discoveredLogEventRepository.findByAgentIdAndEventNameAndSourceClassAndSourceMethod(
                1L,
                "orderCreated",
                "com.example.OrderService",
                "createOrder"
            )
        ).willReturn(Optional.of(existing))
        given(discoveredLogEventRepository.save(existing)).willReturn(existing)

        service.report(
            agentId = 1L,
            request = DiscoveredLogEventReportRequest(
                workerId = "order-service-local-1",
                appName = "order-service",
                appVersion = "0.1.1",
                events = listOf(
                    DiscoveredLogEventItemRequest(
                        eventName = "orderCreated",
                        sourceClass = "com.example.OrderService",
                        sourceMethod = "createOrder",
                        parameterNames = listOf("request")
                    )
                )
            )
        )

        assertThat(existing.parameterNames).containsExactly("request")
        assertThat(existing.appVersion).isEqualTo("0.1.1")
        assertThat(existing.status).isEqualTo(DiscoveredLogEventStatus.IGNORED)
    }

    @Test
    fun `report accepts empty events`() {
        val agent = agent(id = 1L)
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent))

        val response = service.report(
            agentId = 1L,
            request = DiscoveredLogEventReportRequest(
                workerId = "order-service-local-1",
                appName = "order-service",
                events = emptyList()
            )
        )

        assertThat(response.received).isZero()
        assertThat(response.upserted).isZero()
        verify(discoveredLogEventRepository, never()).save(any(DiscoveredLogEvent::class.java))
    }

    @Test
    fun `report rejects agent identity mismatch`() {
        val agent = agent(id = 1L)
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent))

        assertThatThrownBy {
            service.report(
                agentId = 1L,
                request = DiscoveredLogEventReportRequest(
                    workerId = "other-worker",
                    appName = "order-service",
                    events = emptyList()
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `report rejects non camelCase eventName`() {
        val agent = agent(id = 1L)
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent))

        assertThatThrownBy {
            service.report(
                agentId = 1L,
                request = DiscoveredLogEventReportRequest(
                    workerId = "order-service-local-1",
                    appName = "order-service",
                    events = listOf(
                        DiscoveredLogEventItemRequest(
                            eventName = "OrderCreated",
                            sourceClass = "com.example.OrderService",
                            sourceMethod = "createOrder"
                        )
                    )
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST)
    }

    @Test
    fun `report rejects more than 500 events`() {
        val agent = agent(id = 1L)
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent))
        val events = (1..501).map {
            DiscoveredLogEventItemRequest(
                eventName = "orderCreated$it",
                sourceClass = "com.example.OrderService",
                sourceMethod = "createOrder"
            )
        }

        assertThatThrownBy {
            service.report(
                agentId = 1L,
                request = DiscoveredLogEventReportRequest(
                    workerId = "order-service-local-1",
                    appName = "order-service",
                    events = events
                )
            )
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST)
    }

    private fun agent(id: Long): Agent =
        Agent(
            workerId = "order-service-local-1",
            appName = "order-service"
        ).also {
            ReflectionTestUtils.setField(it, "id", id)
        }
}
