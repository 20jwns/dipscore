package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.trading.TradingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 계좌/포지션/체결 처리 불가를 422 로 변환한다 (매매 신호 핸들러와 동일 스타일). {@link com.dipscore.backend.trading.OrderException} 도 포함(하위타입). */
@RestControllerAdvice
public class TradingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TradingExceptionHandler.class);

    @ExceptionHandler(TradingException.class)
    public ResponseEntity<Map<String, Object>> handle(TradingException e) {
        log.warn("계좌/체결 처리 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "trading_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
