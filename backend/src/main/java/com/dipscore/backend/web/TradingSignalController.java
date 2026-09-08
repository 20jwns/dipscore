package com.dipscore.backend.web;

import com.dipscore.backend.signal.TradingSignalResult;
import com.dipscore.backend.signal.TradingSignalService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매수 신호 판정 조회. ({@code signal.enabled=true} 일 때만 등록)
 * 예: {@code GET /api/trading-signal/005930} → "매수신호" / "대기" + 조건별 상세.
 */
@RestController
@RequestMapping("/api/trading-signal")
@ConditionalOnProperty(prefix = "signal", name = "enabled", havingValue = "true")
public class TradingSignalController {

    private final TradingSignalService tradingSignalService;

    public TradingSignalController(TradingSignalService tradingSignalService) {
        this.tradingSignalService = tradingSignalService;
    }

    @GetMapping("/{symbol}")
    public TradingSignalResult signal(@PathVariable String symbol) {
        return tradingSignalService.evaluateBuySignal(symbol);
    }
}
