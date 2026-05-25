package com.logfriends.platform.domain.fieldrequest.repository

import com.logfriends.platform.domain.fieldrequest.entity.FieldRequest
import com.logfriends.platform.domain.fieldrequest.entity.FieldRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FieldRequestRepository : JpaRepository<FieldRequest, Long> {

    fun findAllByAppNameAndEventName(appName: String, eventName: String): List<FieldRequest>

    fun existsByAppNameAndEventNameAndRequestedFieldNameAndStatusIn(
        appName: String,
        eventName: String,
        requestedFieldName: String,
        statuses: Collection<FieldRequestStatus>
    ): Boolean

    @Query(
        """
        SELECT r FROM FieldRequest r
        WHERE r.appName = :appName
        AND r.eventName IN :eventNames
        """
    )
    fun findAllByAppNameAndEventNameIn(appName: String, eventNames: Collection<String>): List<FieldRequest>
}
