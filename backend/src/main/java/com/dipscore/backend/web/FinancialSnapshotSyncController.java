package com.dipscore.backend.web;

import java.util.List;

import com.dipscore.backend.attractiveness.ingest.FinancialSnapshotSyncJob;
import com.dipscore.backend.attractiveness.ingest.FinancialSnapshotSyncJob.FinancialSnapshotSyncReport;
import com.dipscore.backend.marketdata.dailybatch.DailyBatchProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재무제표 적재 수동 트리거 (스케줄과 별개로 즉시 1회). 테스트/보강용.
 * ({@code attractiveness.enabled} + {@code attractiveness.financial-sync.enabled} 둘 다 true 일 때만 등록)
 *
 * <p>{@code POST /api/admin/financial-snapshot-sync} — {@code daily-batch.symbols} · 기본 회계연도(작년).
 * <br>{@code ?symbols=005930,000660&fiscalYear=2024} — 지정.
 */
@RestController
@ConditionalOnProperty(
        name = {"attractiveness.enabled", "attractiveness.financial-sync.enabled"},
        havingValue = "true")
public class FinancialSnapshotSyncController {

    private final FinancialSnapshotSyncJob job;
    private final DailyBatchProperties batchProps;

    public FinancialSnapshotSyncController(FinancialSnapshotSyncJob job, DailyBatchProperties batchProps) {
        this.job = job;
        this.batchProps = batchProps;
    }

    @PostMapping("/api/admin/financial-snapshot-sync")
    public FinancialSnapshotSyncReport run(@RequestParam(required = false) List<String> symbols,
                                           @RequestParam(required = false) Integer fiscalYear) {
        List<String> targets = symbols == null || symbols.isEmpty() ? batchProps.symbols() : symbols;
        return job.sync(targets, fiscalYear != null ? fiscalYear : job.defaultFiscalYear());
    }
}
