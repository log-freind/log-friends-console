package com.logfriends.platform.overview.reliability

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ReliabilityOverviewService(
    private val repository: ReliabilityOverviewRepository
) {
    fun getOverview(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): ReliabilityOverviewResponse {
        if (!from.isBefore(to)) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "from must be before to")
        }
        if (limit !in 1..100) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, "limit must be between 1 and 100")
        }

        val normalizedAppName = appName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedWorkerId = workerId?.trim()?.takeIf { it.isNotEmpty() }
        val httpCounts = repository.getHttpSummary(from, to, normalizedAppName, normalizedWorkerId)
        val failedEvents = repository.getFailedEventCount(from, to, normalizedAppName, normalizedWorkerId)

        return ReliabilityOverviewResponse(
            http = HttpReliabilitySummary(
                totalRequests = httpCounts.totalRequests,
                errorRequests = httpCounts.errorRequests,
                errorRate = if (httpCounts.totalRequests == 0L) {
                    0.0
                } else {
                    httpCounts.errorRequests.toDouble() / httpCounts.totalRequests
                }
            ),
            ingest = IngestReliabilitySummary(failedEvents = failedEvents),
            topHttpErrors = repository.getTopHttpErrors(
                from, to, normalizedAppName, normalizedWorkerId, limit
            ),
            topIngestFailures = repository.getTopIngestFailures(
                from, to, normalizedAppName, normalizedWorkerId, limit
            )
        )
    }
}
