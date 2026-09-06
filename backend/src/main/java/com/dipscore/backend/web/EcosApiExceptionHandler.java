package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import com.dipscore.backend.external.ecos.EcosApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 외부(한국은행 ECOS) 연동 실패를 502 로 변환한다. (토스/DART 와 동일 패턴) */
@RestControllerAdvice
public class EcosApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EcosApiExceptionHandler.class);

    @ExceptionHandler(EcosApiException.class)
    public ResponseEntity<Map<String, Object>> handle(EcosApiException e) {
        log.warn("ECOS 연동 오류: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "ecos_api_error",
                "message", e.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
