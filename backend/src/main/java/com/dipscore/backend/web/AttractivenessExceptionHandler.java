package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.attractiveness.AttractivenessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 매력도 계산 불가를 422 로 변환한다 (외부 API 오류의 502 와 같은 스타일이나,
 * 로컬 데이터 부재이므로 상태코드는 422 Unprocessable Entity).
 */
@RestControllerAdvice
public class AttractivenessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AttractivenessExceptionHandler.class);

    @ExceptionHandler(AttractivenessException.class)
    public ResponseEntity<Map<String, Object>> handle(AttractivenessException e) {
        log.warn("매력도 계산 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "attractiveness_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
