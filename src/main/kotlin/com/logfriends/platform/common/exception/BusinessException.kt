package com.logfriends.platform.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String
) {
    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    CONFLICT(HttpStatus.CONFLICT, "이미 존재하는 리소스입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류"),

    // Ingest & Limits
    BATCH_TOO_LARGE(HttpStatus.BAD_REQUEST, "단일 배치 이벤트 수가 허용 한도(50개)를 초과했습니다"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인스턴스 요청 속도 제한을 초과했습니다"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 바디 크기가 허용 한도(256KB)를 초과했습니다"),

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
