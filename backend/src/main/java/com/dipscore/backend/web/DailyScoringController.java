package com.dipscore.backend.web;

import java.util.List;

import com.dipscore.backend.marketdata.dailybatch.DailyBatchProperties;
import com.dipscore.backend.marketdata.dailybatch.DailyScoringJob;
import com.dipscore.backend.marketdata.dailybatch.DailyScoringJob.DailyScoringReport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일일 스코어링(매력도 → 저점진입) 수동 트리거 (스케줄과 별개로 즉시 1회). 테스트/보강용.
 * ({@code attractiveness.enabled} + {@code entry-score.enabled} + {@code daily-batch.scoring.enabled} 모두 true 일 때만 등록)
 *
 * <p>{@code POST /api/admin/daily-scoring} — {@code daily-batch.symbols} 대상.
 * <br>{@code ?symbols=005930,000660} — 지정 종목만.
 */
@RestController
@ConditionalOnProperty(
        name = {"attractiveness.enabled", "entry-score.enabled", "daily-batch.scoring.enabled"},
        havingValue = "true")
public class DailyScoringController {

    private final DailyScoringJob job;
    private final DailyBatchProperties batchProps;

    public DailyScoringController(DailyScoringJob job, DailyBatchProperties batchProps) {
        this.job = job;
        this.batchProps = batchProps;
    }

    @PostMapping("/api/admin/daily-scoring")
    public DailyScoringReport run(@RequestParam(required = false) List<String> symbols) {
        return job.run(symbols == null || symbols.isEmpty() ? batchProps.symbols() : symbols);
    }
}
