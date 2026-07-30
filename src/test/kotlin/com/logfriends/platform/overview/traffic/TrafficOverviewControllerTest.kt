package com.logfriends.platform.overview.traffic

import com.logfriends.platform.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class TrafficOverviewControllerTest {
    private val repository: TrafficOverviewRepository = mock()
    private val controller = TrafficOverviewController(repository)
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `returns traffic items with default limit`() {
        val from = Instant.parse("2026-07-30T00:00:00Z")
        val to = Instant.parse("2026-07-30T01:00:00Z")
        val items = listOf(TrafficOverviewItem("GET", "/x", 1))
        given(repository.findTopTraffic(from, to, "orders", "worker-1", 10)).willReturn(items)

        mockMvc.get("/api/overview/traffic") {
            param("from", from.toString())
            param("to", to.toString())
            param("appName", "orders")
            param("workerId", "worker-1")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].method") { value("GET") }
                jsonPath("$.items[0].uri") { value("/x") }
                jsonPath("$.items[0].requestCount") { value(1) }
            }

        verify(repository).findTopTraffic(from, to, "orders", "worker-1", 10)
    }

    @Test
    fun `rejects limit above 100`() {
        val from = Instant.parse("2026-07-30T00:00:00Z")
        val to = Instant.parse("2026-07-30T01:00:00Z")

        assertThatThrownBy {
            controller.getTraffic(from, to, null, null, 101)
        }.isInstanceOf(BusinessException::class.java)
    }
}
