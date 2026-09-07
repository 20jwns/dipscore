package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.entryscore.EntryScoreException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 저점 진입 스코어 계산 불가를 422 로 변환한다 (매력도 핸들러와 동일 스타일). */
@RestControllerAdvice
public class EntryScoreExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EntryScoreExceptionHandler.class);

    @ExceptionHandler(EntryScoreException.class)
    public ResponseEntity<Map<String, Object>> handle(EntryScoreException e) {
        log.warn("저점 진입 스코어 계산 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "entry_score_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
