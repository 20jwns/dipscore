package com.dipscore.backend.web;

import java.util.List;

import com.dipscore.backend.marketdata.dailybatch.DailyBatchProperties;
import com.dipscore.backend.marketdata.dailybatch.DailyCandleSyncJob;
import com.dipscore.backend.marketdata.dailybatch.DailyCandleSyncJob.DailyCandleSyncReport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 최신 일봉 적재 수동 트리거 (스케줄과 별개로 즉시 1회 실행). 테스트/보강용.
 * ({@code daily-batch.daily-candle.enabled=true} 일 때만 등록)
 *
 * <p>{@code POST /api/admin/daily-candle-sync} — {@code daily-batch.symbols} 대상.
 * <br>{@code ?symbols=005930,000660} — 지정 종목만.
 */
@RestController
@ConditionalOnProperty(prefix = "daily-batch.daily-candle", name = "enabled", havingValue = "true")
public class DailyCandleSyncController {

    private final DailyCandleSyncJob job;
    private final DailyBatchProperties batchProps;

    public DailyCandleSyncController(DailyCandleSyncJob job, DailyBatchProperties batchProps) {
        this.job = job;
        this.batchProps = batchProps;
    }

    @PostMapping("/api/admin/daily-candle-sync")
    public DailyCandleSyncReport run(@RequestParam(required = false) List<String> symbols) {
        return job.sync(symbols == null || symbols.isEmpty() ? batchProps.symbols() : symbols);
    }
}
