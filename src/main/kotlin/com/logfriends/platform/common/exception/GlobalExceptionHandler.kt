package com.logfriends.platform.common.exception

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    data class ErrorResponse(
        val code: String,
        val message: String,
        val timestamp: Instant = Instant.now()
    )

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        log.warn("Business exception: {} - {}", e.errorCode, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.name, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse("VALIDATION_ERROR", message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", e)
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse("INTERNAL_ERROR", "내부 서버 오류가 발생했습니다"))
    }
}
