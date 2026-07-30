package com.logfriends.platform.overview.performance

import com.logfriends.platform.common.exception.BusinessException
import com.logfriends.platform.common.exception.ErrorCode
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PerformanceOverviewService(
    private val repository: PerformanceOverviewRepository
) {
    fun getPerformanceOverview(
        from: Instant,
        to: Instant,
        appName: String?,
        workerId: String?,
        limit: Int
    ): PerformanceOverviewResponse {
        if (!from.isBefore(to)) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "from must be before to")
        }
        if (limit !in 1..100) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "limit must be between 1 and 100")
        }

        return PerformanceOverviewResponse(
            repository.findSlowEndpoints(from, to, appName, workerId, limit)
        )
    }
}
