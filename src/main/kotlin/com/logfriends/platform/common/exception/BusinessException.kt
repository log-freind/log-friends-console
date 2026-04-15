package com.logfriends.platform.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류"),

    // Agent
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "에이전트를 찾을 수 없습니다"),
    AGENT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 에이전트입니다"),

    // AlertRule
    ALERT_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 규칙을 찾을 수 없습니다"),
    INVALID_ALERT_CONDITION(HttpStatus.BAD_REQUEST, "알림 조건이 유효하지 않습니다"),

    // Incident
    INCIDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "인시던트를 찾을 수 없습니다"),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "유효하지 않은 상태 변경입니다"),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "다른 사용자가 먼저 수정했습니다. 다시 시도해주세요"),
}

class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)
