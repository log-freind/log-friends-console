package com.logfriends.platform.domain.fieldrequest.entity

import com.logfriends.platform.common.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "field_requests",
    indexes = [
        Index(name = "idx_field_requests_app_event", columnList = "app_name, event_name"),
        Index(name = "idx_field_requests_status", columnList = "status")
    ]
)
class FieldRequest(

    @Column(name = "app_name", nullable = false)
    val appName: String,

    @Column(name = "event_name", nullable = false)
    val eventName: String,

    @Column(name = "requested_field_name", nullable = false)
    val requestedFieldName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_type", nullable = false)
    val requestedType: FieldType,

    @Column(nullable = false)
    val reason: String,

    @Column(name = "requested_by")
    val requestedBy: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FieldRequestStatus = FieldRequestStatus.REQUESTED
) : BaseEntity() {

    fun transitionTo(next: FieldRequestStatus) {
        if (!status.canTransitionTo(next)) {
            throw IllegalArgumentException("Cannot change field request status from $status to $next")
        }
        status = next
    }
}

enum class FieldRequestStatus {
    REQUESTED,
    ACCEPTED,
    DONE,
    REJECTED;

    fun isOpen(): Boolean = this == REQUESTED || this == ACCEPTED

    fun canTransitionTo(next: FieldRequestStatus): Boolean =
        when (this) {
            REQUESTED -> next == ACCEPTED || next == REJECTED
            ACCEPTED -> next == DONE || next == REJECTED
            DONE -> false
            REJECTED -> false
        }
}

enum class FieldType {
    STRING,
    INT,
    LONG,
    DOUBLE,
    BOOLEAN,
    DATETIME,
    JSON
}
