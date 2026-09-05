package com.dipscore.backend.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스캐폴딩용 Hello World 엔드포인트. 비즈니스 로직은 아직 없음.
 */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, Object> hello() {
        return Map.of(
                "service", "dipscore-backend",
                "message", "Hello, DipScore!",
                "timestamp", Instant.now().toString()
        );
    }
}
