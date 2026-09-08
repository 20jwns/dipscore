package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.signal.SignalException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 매매 신호 판정 불가를 422 로 변환한다 (매력도/저점진입 핸들러와 동일 스타일). */
@RestControllerAdvice
public class SignalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalExceptionHandler.class);

    @ExceptionHandler(SignalException.class)
    public ResponseEntity<Map<String, Object>> handle(SignalException e) {
        log.warn("매매 신호 판정 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "trading_signal_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
