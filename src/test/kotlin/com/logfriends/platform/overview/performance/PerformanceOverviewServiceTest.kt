package com.logfriends.platform.overview.performance

import com.logfriends.platform.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant

class PerformanceOverviewServiceTest {
    private val repository: PerformanceOverviewRepository = mock()
    private val service = PerformanceOverviewService(repository)
    private val from = Instant.parse("2026-07-30T00:00:00Z")
    private val to = Instant.parse("2026-07-30T01:00:00Z")

    @Test
    fun `returns endpoint performance items with filters`() {
        val item = PerformanceOverviewItem("GET", "/x", 1, 12.3, 20.0, 25)
        given(repository.findSlowEndpoints(from, to, "orders", "worker-1", 10))
            .willReturn(listOf(item))

        val response = service.getPerformanceOverview(from, to, "orders", "worker-1", 10)

        assertThat(response.items).containsExactly(item)
        verify(repository).findSlowEndpoints(from, to, "orders", "worker-1", 10)
    }

    @Test
    fun `rejects a range whose from is not before to`() {
        assertThatThrownBy {
            service.getPerformanceOverview(to, to, null, null, 10)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("from must be before to")
    }

    @Test
    fun `rejects limit outside one through one hundred`() {
        assertThatThrownBy {
            service.getPerformanceOverview(from, to, null, null, 101)
        }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("limit must be between 1 and 100")
    }
}
