package com.dipscore.backend.web;

import java.util.List;

import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshJob;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshJob.MacroSyncReport;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 거시지표 갱신 수동 트리거 (스케줄과 별개로 즉시 1회). 테스트/보강용.
 * ({@code attractiveness.enabled} + {@code attractiveness.macro-sync.enabled} 둘 다 true 일 때만 등록)
 *
 * <p>{@code POST /api/admin/macro-sync} — 3지표(BASE_RATE·USD_KRW·CPI) 최신값 갱신.
 * <br>{@code ?codes=USD_KRW,CPI} — 지정 지표만.
 */
@RestController
@ConditionalOnProperty(
        name = {"attractiveness.enabled", "attractiveness.macro-sync.enabled"},
        havingValue = "true")
public class MacroIndicatorSyncController {

    private final MacroIndicatorRefreshJob job;

    public MacroIndicatorSyncController(MacroIndicatorRefreshJob job) {
        this.job = job;
    }

    @PostMapping("/api/admin/macro-sync")
    public MacroSyncReport run(@RequestParam(required = false) List<String> codes) {
        return job.sync(codes == null || codes.isEmpty() ? MacroIndicatorRefreshService.ALL_CODES : codes);
    }
}
