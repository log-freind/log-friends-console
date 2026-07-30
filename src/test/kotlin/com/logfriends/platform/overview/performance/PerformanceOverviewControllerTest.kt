package com.logfriends.platform.overview.performance

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class PerformanceOverviewControllerTest {
    private val service: PerformanceOverviewService = mock()
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(PerformanceOverviewController(service))
        .build()

    @Test
    fun `uses limit ten by default`() {
        val from = Instant.parse("2026-07-30T00:00:00Z")
        val to = Instant.parse("2026-07-30T01:00:00Z")
        given(service.getPerformanceOverview(from, to, null, null, 10))
            .willReturn(PerformanceOverviewResponse(emptyList()))

        mockMvc.get("/api/overview/performance") {
            param("from", from.toString())
            param("to", to.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.items") { isArray() }
        }

        verify(service).getPerformanceOverview(from, to, null, null, 10)
    }

    @Test
    fun `requires from and to query parameters`() {
        mockMvc.get("/api/overview/performance") {
            param("to", "2026-07-30T01:00:00Z")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }

    @Test
    fun `rejects a non ISO timestamp`() {
        mockMvc.get("/api/overview/performance") {
            param("from", "not-a-timestamp")
            param("to", "2026-07-30T01:00:00Z")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_INPUT") }
        }
    }
}
