package com.logfriends.platform.api.rest.overview.business

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.domain.overview.business.BusinessEventCount
import com.logfriends.platform.domain.overview.business.BusinessOverviewService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class BusinessOverviewControllerTest {

    private val service: BusinessOverviewService = mock()
    private val controller = BusinessOverviewController(service)
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    private val from = Instant.parse("2026-07-01T00:00:00Z")
    private val to = Instant.parse("2026-07-02T00:00:00Z")

    @Test
    fun `returns business event counts and forwards filters with default limit`() {
        given(service.getOverview(from, to, "shop", "worker-1", 10))
            .willReturn(listOf(BusinessEventCount("cartItemAdded", 10)))

        mockMvc.get("/api/overview/business") {
            param("from", from.toString())
            param("to", to.toString())
            param("appName", "shop")
            param("workerId", "worker-1")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[0].eventName") { value("cartItemAdded") }
                jsonPath("$.items[0].eventCount") { value(10) }
            }

        verify(service).getOverview(from, to, "shop", "worker-1", 10)
    }

    @Test
    fun `rejects limit above one hundred`() {
        assertThatThrownBy {
            controller.getOverview(from, to, null, null, 101)
        }.isInstanceOf(BusinessException::class.java)
    }

    @Test
    fun `requires ISO 8601 from and to`() {
        mockMvc.get("/api/overview/business") {
            param("from", from.toString())
        }.andExpect {
            status { isBadRequest() }
        }

        mockMvc.get("/api/overview/business") {
            param("from", "2026-07-01")
            param("to", to.toString())
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
