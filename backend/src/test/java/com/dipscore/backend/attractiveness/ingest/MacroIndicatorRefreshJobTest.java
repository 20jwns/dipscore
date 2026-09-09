package com.dipscore.backend.attractiveness.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshJob.MacroSyncReport;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService.MacroRefreshResult;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService.RefreshStatus;
import com.dipscore.backend.external.ecos.EcosApiException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorRefreshJobTest {

    @Mock MacroIndicatorRefreshService service;

    private MacroIndicatorRefreshJob job() {
        return new MacroIndicatorRefreshJob(service,
                new MacroIndicatorSyncProperties(true, "0 0 6 * * *", "0 0 6 1 * *"));
    }

    private static MacroRefreshResult inserted(String code) {
        return new MacroRefreshResult(code, Instant.parse("2026-09-08T00:00:00Z"),
                new BigDecimal("1"), RefreshStatus.INSERTED, null);
    }

    private static MacroRefreshResult skipped(String code) {
        return new MacroRefreshResult(code, Instant.parse("2026-09-08T00:00:00Z"),
                new BigDecimal("1"), RefreshStatus.SKIPPED_EXISTS, null);
    }

    @Test
    void 환율_스케줄은_USD_KRW만_갱신한다() {
        when(service.refreshLatest("USD_KRW")).thenReturn(inserted("USD_KRW"));

        job().refreshExchangeRate();

        verify(service).refreshLatest("USD_KRW");
        verify(service, never()).refreshLatest("BASE_RATE");
        verify(service, never()).refreshLatest("CPI");
    }

    @Test
    void 월간_스케줄은_기준금리와_CPI를_갱신한다() {
        when(service.refreshLatest("BASE_RATE")).thenReturn(inserted("BASE_RATE"));
        when(service.refreshLatest("CPI")).thenReturn(inserted("CPI"));

        job().refreshMonthlyIndicators();

        verify(service).refreshLatest("BASE_RATE");
        verify(service).refreshLatest("CPI");
        verify(service, never()).refreshLatest("USD_KRW");
    }

    @Test
    void 한_지표_실패해도_나머지_지표는_계속한다() {
        when(service.refreshLatest("BASE_RATE")).thenThrow(new EcosApiException("ECOS 조회 실패: HTTP 500"));
        when(service.refreshLatest("CPI")).thenReturn(inserted("CPI"));

        MacroSyncReport report = job().sync(List.of("BASE_RATE", "CPI"));

        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.indicatorCode()).isEqualTo("BASE_RATE");
            assertThat(r.status()).isEqualTo(RefreshStatus.FAILED);
            assertThat(r.error()).contains("HTTP 500");
        });
    }

    @Test
    void skip와_insert를_집계한다() {
        when(service.refreshLatest("USD_KRW")).thenReturn(inserted("USD_KRW"));
        when(service.refreshLatest("CPI")).thenReturn(skipped("CPI"));

        MacroSyncReport report = job().sync(List.of("USD_KRW", "CPI"));

        assertThat(report.requested()).isEqualTo(2);
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.failed()).isZero();
    }

    @Test
    void 대상_지표가_없으면_아무것도_하지_않는다() {
        MacroSyncReport report = job().sync(List.of());

        assertThat(report.requested()).isZero();
        verifyNoInteractions(service);
    }

    @Test
    void 알_수_없는_예외도_삼키고_FAILED로_집계한다() {
        when(service.refreshLatest(anyString())).thenThrow(new RuntimeException("boom"));

        MacroSyncReport report = job().sync(List.of("USD_KRW"));

        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.results().get(0).status()).isEqualTo(RefreshStatus.FAILED);
    }
}
