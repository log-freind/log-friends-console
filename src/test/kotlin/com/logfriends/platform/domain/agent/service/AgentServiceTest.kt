package com.logfriends.platform.domain.agent.service

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import com.logfriends.platform.domain.agent.entity.Agent
import com.logfriends.platform.domain.agent.entity.AgentStatus
import com.logfriends.platform.domain.agent.repository.AgentRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AgentServiceTest {

    private val agentRepository: AgentRepository = mock()
    private val agentService = AgentService(agentRepository)

    @Test
    fun `register stores app name and runtime metadata`() {
        given(agentRepository.existsByWorkerId("demo-worker-1")).willReturn(false)
        given(agentRepository.save(ArgumentMatchers.any(Agent::class.java))).willAnswer { it.arguments[0] }

        val result = agentService.register(
            workerId = "demo-worker-1",
            appName = "demo-app",
            sdkVersion = "local-test",
            javaVersion = "21",
            hostname = "local",
            metadata = mapOf("env" to "test")
        )

        assertThat(result.workerId).isEqualTo("demo-worker-1")
        assertThat(result.appName).isEqualTo("demo-app")
        assertThat(result.sdkVersion).isEqualTo("local-test")
        assertThat(result.javaVersion).isEqualTo("21")
        assertThat(result.hostname).isEqualTo("local")
        assertThat(result.metadata).containsEntry("env", "test")
        assertThat(result.status).isEqualTo(AgentStatus.RUNNING)
        assertThat(result.lastHeartbeat).isNotNull()
    }

    @Test
    fun `known worker heartbeat updates existing agent`() {
        val agent = Agent(
            workerId = "demo-worker-1",
            appName = "demo-app",
            status = AgentStatus.UNKNOWN
        )
        given(agentRepository.findByWorkerId("demo-worker-1")).willReturn(Optional.of(agent))
        given(agentRepository.save(agent)).willReturn(agent)

        val result = agentService.heartbeat(
            workerId = "demo-worker-1",
            metadata = mapOf("hostname" to "local")
        )

        assertThat(result.status).isEqualTo(AgentStatus.RUNNING)
        assertThat(result.lastHeartbeat).isNotNull()
        assertThat(result.metadata).containsEntry("hostname", "local")
        verify(agentRepository).save(agent)
    }

    @Test
    fun `unknown worker heartbeat does not create agent`() {
        given(agentRepository.findByWorkerId("unknown-worker")).willReturn(Optional.empty())

        assertThatThrownBy {
            agentService.heartbeat("unknown-worker")
        }
            .isInstanceOf(BusinessException::class.java)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AGENT_NOT_FOUND)

        verify(agentRepository, never()).save(ArgumentMatchers.any(Agent::class.java))
    }
}
