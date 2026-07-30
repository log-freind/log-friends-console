package com.logfriends.platform.overview.reliability

import com.logfriends.platform.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.time.Instant

class ReliabilityOverviewServiceTest {
    private val repository: ReliabilityOverviewRepository = mock()
    private val service = ReliabilityOverviewService(repository)
    private val from = Instant.parse("2026-07-30T00:00:00Z")
    private val to = Instant.parse("2026-07-31T00:00:00Z")

    @Test
    fun `returns summaries and ranked failures with normalized filters`() {
        given(repository.getHttpSummary(from, to, "orders", "worker-1"))
            .willReturn(HttpCounts(totalRequests = 20, errorRequests = 5))
        given(repository.getFailedEventCount(from, to, "orders", "worker-1"))
            .willReturn(3)
        given(repository.getTopHttpErrors(from, to, "orders", "worker-1", 10))
            .willReturn(listOf(TopHttpError("GET", "/orders", 500, 4)))
        given(repository.getTopIngestFailures(from, to, "orders", "worker-1", 10))
            .willReturn(listOf(TopIngestFailure("INVALID_TIMESTAMP", 3)))

        val result = service.getOverview(from, to, " orders ", " worker-1 ", 10)

        assertThat(result.http).isEqualTo(HttpReliabilitySummary(20, 5, 0.25))
        assertThat(result.ingest).isEqualTo(IngestReliabilitySummary(3))
        assertThat(result.topHttpErrors)
            .containsExactly(TopHttpError("GET", "/orders", 500, 4))
        assertThat(result.topIngestFailures)
            .containsExactly(TopIngestFailure("INVALID_TIMESTAMP", 3))
    }

    @Test
    fun `returns zero error rate when there are no requests`() {
        given(repository.getHttpSummary(from, to, null, null))
            .willReturn(HttpCounts(totalRequests = 0, errorRequests = 0))
        given(repository.getFailedEventCount(from, to, null, null)).willReturn(0)
        given(repository.getTopHttpErrors(from, to, null, null, 10)).willReturn(emptyList())
        given(repository.getTopIngestFailures(from, to, null, null, 10)).willReturn(emptyList())

        val result = service.getOverview(from, to, null, null, 10)

        assertThat(result.http.errorRate).isZero()
    }

    @Test
    fun `rejects invalid time range before querying`() {
        assertThatThrownBy { service.getOverview(to, from, null, null, 10) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("from must be before to")
    }

    @Test
    fun `rejects limit above 100`() {
        assertThatThrownBy { service.getOverview(from, to, null, null, 101) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("limit must be between 1 and 100")
    }
}
