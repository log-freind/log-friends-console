package com.logfriends.platform.ingest

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class IngestBatchProperties(
    @Value("\${logfriends.ingest.db-batch-size:100}")
    val dbBatchSize: Int
) {
    init {
        require(dbBatchSize > 0) {
            "logfriends.ingest.db-batch-size must be greater than zero"
        }
    }
}
