package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.external.dart.DartApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 외부(DART) 연동 실패를 502 로 변환한다. (토스 {@code TossApiExceptionHandler} 와 동일 패턴) */
@RestControllerAdvice
public class DartApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DartApiExceptionHandler.class);

    @ExceptionHandler(DartApiException.class)
    public ResponseEntity<Map<String, Object>> handle(DartApiException e) {
        log.warn("DART 연동 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "dart_api_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
