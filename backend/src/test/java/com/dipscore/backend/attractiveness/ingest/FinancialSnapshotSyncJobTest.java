package com.dipscore.backend.attractiveness.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.dipscore.backend.attractiveness.AttractivenessProperties;
import com.dipscore.backend.attractiveness.ingest.FinancialSnapshotSyncJob.FinancialSnapshotSyncReport;
import com.dipscore.backend.attractiveness.ingest.FinancialSnapshotSyncJob.Status;
import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.corpcode.DartCorpCodeClient;
import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.dipscore.backend.marketdata.dailybatch.DailyBatchProperties;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;
import com.dipscore.backend.marketdata.instrument.Instrument;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialSnapshotSyncJobTest {

    @Mock
    DartFinancialSnapshotIngestionService ingestionService;
    @Mock
    FinancialSnapshotRepository repository;
    @Mock
    InstrumentRepository instrumentRepository;
    @Mock
    DartCorpCodeClient corpCodeClient;

    private FinancialSnapshotSyncJob job() {
        return job(new FinancialSnapshotSyncProperties(true, "0 0 3 * * MON", 0, 0L));
    }

    private FinancialSnapshotSyncJob job(FinancialSnapshotSyncProperties props) {
        return new FinancialSnapshotSyncJob(ingestionService, repository, instrumentRepository, corpCodeClient,
                new DailyBatchProperties(List.of("005930", "000660")),
                new AttractivenessProperties(true, "base-v1", 2024, "CFS", 24, Map.of()),
                props);
    }

    private void instrumentHasCorpCode(String symbol, String corpCode) {
        Instrument inst = mock(Instrument.class);
        when(inst.getCorpCode()).thenReturn(corpCode);
        when(instrumentRepository.findById(symbol)).thenReturn(Optional.of(inst));
    }

    @Test
    void 이미_있는_회계연도는_skip하고_새_연도만_적재한다() {
        instrumentHasCorpCode("005930", "CA");
        instrumentHasCorpCode("000660", "CB");
        when(repository.findBySymbolAndFiscalYearAndFsDiv("005930", (short) 2025, "CFS"))
                .thenReturn(Optional.of(mock(FinancialSnapshot.class)));
        when(repository.findBySymbolAndFiscalYearAndFsDiv("000660", (short) 2025, "CFS"))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestLatest("000660", "CB", 2025, "CFS"))
                .thenReturn(mock(FinancialSnapshot.class));

        FinancialSnapshotSyncReport report = job().sync(List.of("005930", "000660"), 2025);

        verify(ingestionService).ingestLatest("000660", "CB", 2025, "CFS");
        verify(ingestionService, never()).ingestLatest(eq("005930"), any(), anyInt(), any());
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.failed()).isZero();
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.status()).isEqualTo(Status.SKIPPED_EXISTS);
        });
    }

    @Test
    void corp_code가_instrument에_없으면_corpCode_클라이언트로_폴백한다() {
        Instrument noCode = mock(Instrument.class);
        when(noCode.getCorpCode()).thenReturn(null);
        when(instrumentRepository.findById("005930")).thenReturn(Optional.of(noCode));
        when(corpCodeClient.findByStockCode("005930"))
                .thenReturn(Optional.of(new DartCorpCode("00126380", "삼성전자", "005930", "20170630")));
        when(repository.findBySymbolAndFiscalYearAndFsDiv("005930", (short) 2025, "CFS"))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestLatest("005930", "00126380", 2025, "CFS"))
                .thenReturn(mock(FinancialSnapshot.class));

        FinancialSnapshotSyncReport report = job().sync(List.of("005930"), 2025);

        verify(ingestionService).ingestLatest("005930", "00126380", 2025, "CFS");
        assertThat(report.inserted()).isEqualTo(1);
    }

    @Test
    void corp_code를_어디서도_못찾으면_skip한다() {
        when(instrumentRepository.findById("005930")).thenReturn(Optional.empty());
        when(corpCodeClient.findByStockCode("005930")).thenReturn(Optional.empty());

        FinancialSnapshotSyncReport report = job().sync(List.of("005930"), 2025);

        verify(ingestionService, never()).ingestLatest(any(), any(), anyInt(), any());
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.inserted()).isZero();
        assertThat(report.results().get(0).status()).isEqualTo(Status.SKIPPED_NO_CORP_CODE);
    }

    @Test
    void 한_종목_적재_실패해도_다음_종목은_계속한다() {
        instrumentHasCorpCode("005930", "CA");
        instrumentHasCorpCode("000660", "CB");
        when(repository.findBySymbolAndFiscalYearAndFsDiv(any(), eq((short) 2025), eq("CFS")))
                .thenReturn(Optional.empty());
        when(ingestionService.ingestLatest("005930", "CA", 2025, "CFS"))
                .thenThrow(new DartApiException("boom"));
        when(ingestionService.ingestLatest("000660", "CB", 2025, "CFS"))
                .thenReturn(mock(FinancialSnapshot.class));

        FinancialSnapshotSyncReport report = job().sync(List.of("005930", "000660"), 2025);

        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.status()).isEqualTo(Status.FAILED);
            assertThat(r.error()).contains("boom");
        });
    }

    @Test
    void 대상_종목이_없으면_아무것도_하지_않는다() {
        FinancialSnapshotSyncReport report = job().sync(List.of(), 2025);

        assertThat(report.requested()).isZero();
        verifyNoInteractions(ingestionService, repository, instrumentRepository, corpCodeClient);
    }

    @Test
    void defaultFiscalYear는_설정값_우선이고_0이면_작년() {
        assertThat(job(new FinancialSnapshotSyncProperties(true, "c", 2021, 0L)).defaultFiscalYear())
                .isEqualTo(2021);

        int lastYear = Year.now(ZoneId.of("Asia/Seoul")).getValue() - 1;
        assertThat(job().defaultFiscalYear()).isEqualTo(lastYear);
    }
}
