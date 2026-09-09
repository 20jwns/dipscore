package com.dipscore.backend.marketdata.dailybatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.dipscore.backend.attractiveness.AttractivenessException;
import com.dipscore.backend.attractiveness.AttractivenessResult;
import com.dipscore.backend.attractiveness.BaseScoreService;
import com.dipscore.backend.entryscore.EntryScoreException;
import com.dipscore.backend.entryscore.EntryScoreResult;
import com.dipscore.backend.entryscore.EntryScoreService;
import com.dipscore.backend.marketdata.dailybatch.DailyScoringJob.DailyScoringReport;
import com.dipscore.backend.marketdata.dailybatch.DailyScoringJob.Status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyScoringJobTest {

    @Mock BaseScoreService baseScoreService;
    @Mock EntryScoreService entryScoreService;

    private DailyScoringJob job() {
        return new DailyScoringJob(baseScoreService, entryScoreService,
                new DailyBatchProperties(List.of("005930", "000660")),
                new DailyScoringProperties(true, "0 30 8 * * MON-FRI"));
    }

    private static AttractivenessResult attractiveness(String symbol, double baseScore) {
        return new AttractivenessResult(symbol, Instant.now(), "base-v1", "반도체", 3, 2025, "CFS",
                new BigDecimal("70000"), Map.of(), baseScore, 1.0, baseScore);
    }

    private static EntryScoreResult entry(String symbol, double entryScore, boolean filterPassed) {
        return new EntryScoreResult(symbol, Instant.now(), "entry-v1", 0.3, 0.2, 0.55,
                filterPassed, entryScore, 70.0, Map.of());
    }

    @Test
    void 각_종목_매력도후_저점진입_순서로_계산하고_성공수를_집계한다() {
        when(baseScoreService.computeAndSave("005930")).thenReturn(attractiveness("005930", 58.7));
        when(entryScoreService.computeAndSave("005930")).thenReturn(entry("005930", 62.0, false));
        when(baseScoreService.computeAndSave("000660")).thenReturn(attractiveness("000660", 67.0));
        when(entryScoreService.computeAndSave("000660")).thenReturn(entry("000660", 71.0, true));

        DailyScoringReport report = job().run(List.of("005930", "000660"));

        InOrder ord = inOrder(baseScoreService, entryScoreService);
        ord.verify(baseScoreService).computeAndSave("005930");
        ord.verify(entryScoreService).computeAndSave("005930");
        ord.verify(baseScoreService).computeAndSave("000660");
        ord.verify(entryScoreService).computeAndSave("000660");

        assertThat(report.succeeded()).isEqualTo(2);
        assertThat(report.failed()).isZero();
        assertThat(report.results()).allMatch(r -> r.status() == Status.OK);
        assertThat(report.results().get(1).entryScore()).isEqualTo(71.0);
        assertThat(report.results().get(1).filterPassed()).isTrue();
    }

    @Test
    void 매력도_실패시_저점진입은_건너뛰고_다음_종목_계속한다() {
        when(baseScoreService.computeAndSave("005930"))
                .thenThrow(new AttractivenessException("재무 스냅샷 없음: 005930 CFS (어느 회계연도에도)"));
        when(baseScoreService.computeAndSave("000660")).thenReturn(attractiveness("000660", 67.0));
        when(entryScoreService.computeAndSave("000660")).thenReturn(entry("000660", 71.0, true));

        DailyScoringReport report = job().run(List.of("005930", "000660"));

        verify(entryScoreService, never()).computeAndSave("005930");
        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.status()).isEqualTo(Status.ATTRACTIVENESS_FAILED);
            assertThat(r.baseScore()).isNull();
            assertThat(r.error()).contains("재무 스냅샷 없음");
        });
    }

    @Test
    void 저점진입_실패시_그_종목만_실패로_집계하고_매력도점수는_남긴다() {
        when(baseScoreService.computeAndSave("005930")).thenReturn(attractiveness("005930", 58.7));
        when(entryScoreService.computeAndSave("005930"))
                .thenThrow(new EntryScoreException("일봉 부족: 31개 필요, 12개"));
        when(baseScoreService.computeAndSave("000660")).thenReturn(attractiveness("000660", 67.0));
        when(entryScoreService.computeAndSave("000660")).thenReturn(entry("000660", 71.0, true));

        DailyScoringReport report = job().run(List.of("005930", "000660"));

        assertThat(report.succeeded()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.status()).isEqualTo(Status.ENTRY_SCORE_FAILED);
            assertThat(r.baseScore()).isEqualTo(58.7);   // 매력도는 저장됨
            assertThat(r.entryScore()).isNull();
            assertThat(r.error()).contains("일봉 부족");
        });
    }

    @Test
    void 대상_종목이_없으면_아무것도_하지_않는다() {
        DailyScoringReport report = job().run(List.of());

        assertThat(report.requested()).isZero();
        verifyNoInteractions(baseScoreService, entryScoreService);
    }
}
