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

    // LogSpec
    LOG_SPEC_NOT_FOUND(HttpStatus.NOT_FOUND, "LogSpec을 찾을 수 없습니다"),

    // EventStat
    EVENT_STAT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트 통계를 찾을 수 없습니다"),
}

class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message
) : RuntimeException(message)
