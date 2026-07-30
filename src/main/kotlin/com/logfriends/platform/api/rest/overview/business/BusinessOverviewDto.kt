package com.logfriends.platform.api.rest.overview.business

import com.logfriends.platform.domain.overview.business.BusinessEventCount

data class BusinessOverviewResponse(
    val items: List<BusinessOverviewItem>
)

data class BusinessOverviewItem(
    val eventName: String,
    val eventCount: Long
) {
    companion object {
        fun from(count: BusinessEventCount) = BusinessOverviewItem(
            eventName = count.eventName,
            eventCount = count.eventCount
        )
    }
}
