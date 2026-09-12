package com.dipscore.backend.web;

import java.util.List;

import com.dipscore.backend.marketdata.dailybatch.AutoTradeJob;
import com.dipscore.backend.marketdata.dailybatch.AutoTradeJob.AutoTradeReport;
import com.dipscore.backend.marketdata.dailybatch.AutoTradeProperties;
import com.dipscore.backend.marketdata.dailybatch.DailyBatchProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 모의투자 자동매매(BUY 신호 → 자동 매수 체결) 수동 트리거. 테스트/보강용.
 * ({@code account.enabled} + {@code signal.enabled} + {@code daily-batch.auto-trade.enabled} 모두 true 일 때만 등록)
 *
 * <p>{@code POST /api/admin/auto-trade} — {@code daily-batch.symbols} · 설정된 계좌 대상.
 * <br>{@code ?symbols=005930,000660} — 지정 종목만.
 * <br>{@code ?accountId=1} — 설정 대신 이 계좌로.
 * <br>{@code ?skipMarketHoursGuard=true} — 장중 시간대 가드 우회 (테스트 편의용).
 */
@RestController
@ConditionalOnProperty(
        name = {"account.enabled", "signal.enabled", "daily-batch.auto-trade.enabled"},
        havingValue = "true")
public class AutoTradeController {

    private final AutoTradeJob job;
    private final DailyBatchProperties batchProps;
    private final AutoTradeProperties props;

    public AutoTradeController(AutoTradeJob job, DailyBatchProperties batchProps, AutoTradeProperties props) {
        this.job = job;
        this.batchProps = batchProps;
        this.props = props;
    }

    @PostMapping("/api/admin/auto-trade")
    public AutoTradeReport run(@RequestParam(required = false) List<String> symbols,
                               @RequestParam(required = false) Long accountId,
                               @RequestParam(required = false) Boolean skipMarketHoursGuard) {
        List<String> targets = symbols == null || symbols.isEmpty() ? batchProps.symbols() : symbols;
        Long account = accountId != null ? accountId : props.accountId();
        boolean skip = skipMarketHoursGuard != null && skipMarketHoursGuard;
        return job.run(targets, account, skip);
    }
}
