package com.dipscore.backend.web;

import com.dipscore.backend.attractiveness.AttractivenessResult;
import com.dipscore.backend.attractiveness.BaseScoreService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매력도 지수 기본점수 스모크 테스트용 엔드포인트.
 * ({@code attractiveness.enabled=true} 일 때만 등록 - {@link BaseScoreService} 와 동일 조건)
 */
@RestController
@RequestMapping("/api/attractiveness")
@ConditionalOnProperty(prefix = "attractiveness", name = "enabled", havingValue = "true")
public class AttractivenessController {

    private final BaseScoreService baseScoreService;

    public AttractivenessController(BaseScoreService baseScoreService) {
        this.baseScoreService = baseScoreService;
    }

    /** 예: {@code POST /api/attractiveness/005930} → 삼성전자 기본점수 계산 후 저장. */
    @PostMapping("/{symbol}")
    public AttractivenessResult compute(@PathVariable String symbol) {
        return baseScoreService.computeAndSave(symbol);
    }
}
