package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.external.toss.TossApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 외부(토스증권) 연동 실패를 502 로 변환한다. */
@RestControllerAdvice
public class TossApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TossApiExceptionHandler.class);

    @ExceptionHandler(TossApiException.class)
    public ResponseEntity<Map<String, Object>> handle(TossApiException e) {
        log.warn("토스증권 연동 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "toss_api_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
