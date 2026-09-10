package com.dipscore.backend.web;

import com.dipscore.backend.signal.TradingSignalResult;
import com.dipscore.backend.signal.TradingSignalService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매수 신호 판정 조회. ({@code signal.enabled=true} 일 때만 등록)
 * 예: {@code GET /api/trading-signal/005930} → "매수신호" / "대기" + 조건별 상세.
 * <br>{@code ?accountId=1} 을 주면 "보유현금 > 최소매수단위" 조건도 그 계좌의 실제 잔고로 평가한다.
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
    public TradingSignalResult signal(@PathVariable String symbol,
                                      @RequestParam(required = false) Long accountId) {
        return tradingSignalService.evaluateBuySignal(symbol, accountId);
    }
}
