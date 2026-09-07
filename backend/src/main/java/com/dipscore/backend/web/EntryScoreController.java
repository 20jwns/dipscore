package com.dipscore.backend.web;

import com.dipscore.backend.entryscore.EntryScoreResult;
import com.dipscore.backend.entryscore.EntryScoreService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저점 진입 스코어 스모크 테스트용 엔드포인트.
 * ({@code entry-score.enabled=true} 일 때만 등록 - {@link EntryScoreService} 와 동일 조건)
 */
@RestController
@RequestMapping("/api/entry-score")
@ConditionalOnProperty(prefix = "entry-score", name = "enabled", havingValue = "true")
public class EntryScoreController {

    private final EntryScoreService entryScoreService;

    public EntryScoreController(EntryScoreService entryScoreService) {
        this.entryScoreService = entryScoreService;
    }

    /** 예: {@code POST /api/entry-score/005930} → 삼성전자 저점 진입 스코어 계산 후 저장. */
    @PostMapping("/{symbol}")
    public EntryScoreResult compute(@PathVariable String symbol) {
        return entryScoreService.computeAndSave(symbol);
    }
}
