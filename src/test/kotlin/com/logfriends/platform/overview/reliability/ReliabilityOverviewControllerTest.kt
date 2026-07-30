package com.logfriends.platform.overview.reliability

import com.logfriends.platform.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class ReliabilityOverviewControllerTest {
    private val service: ReliabilityOverviewService = mock()
    private val controller = ReliabilityOverviewController(service)

    @Test
    fun `requires from`() {
        assertThatThrownBy {
            controller.getReliabilityOverview(null, "2026-07-31T00:00:00Z", null, null, 10)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("from is required")
    }

    @Test
    fun `requires ISO-8601 to`() {
        assertThatThrownBy {
            controller.getReliabilityOverview("2026-07-30T00:00:00Z", "2026-07-31", null, null, 10)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("to must be an ISO-8601 instant")
    }
}
