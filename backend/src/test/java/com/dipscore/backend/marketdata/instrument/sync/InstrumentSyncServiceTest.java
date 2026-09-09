package com.dipscore.backend.marketdata.instrument.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.company.DartCompanyClient;
import com.dipscore.backend.external.dart.company.dto.DartCompanyResponse;
import com.dipscore.backend.external.dart.corpcode.DartCorpCodeClient;
import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;
import com.dipscore.backend.marketdata.instrument.sync.InstrumentSyncService.InstrumentSyncReport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentSyncServiceTest {

    @Mock
    DartCorpCodeClient dartCorpCodeClient;
    @Mock
    DartCompanyClient dartCompanyClient;
    @Mock
    InstrumentSyncWriter writer;
    @Mock
    InstrumentRepository instrumentRepository;

    private static final int STALE_DAYS = 3;

    private InstrumentSyncService service(int chunkSize) {
        return service(chunkSize, 20);
    }

    private InstrumentSyncService service(int chunkSize, int maxConsecutiveFailures) {
        return new InstrumentSyncService(dartCorpCodeClient, dartCompanyClient, writer, instrumentRepository,
                new InstrumentSyncProperties(true, true, "0 0 4 * * *", chunkSize, true, true, STALE_DAYS,
                        0L, maxConsecutiveFailures)); // delay 0
    }

    private static DartCorpCode dc(String corpCode, String name, String stockCode) {
        return new DartCorpCode(corpCode, name, stockCode, "20260101");
    }

    private static DartCompanyResponse company(String corpCls) {
        return new DartCompanyResponse("000", "정상", null, null, null, null, null, corpCls,
                null, null, null, null, null, null, null);
    }

    // ── corp_cls 필터 OFF (active 미변경) ──────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void 상장종목만_필터링하고_stockCode_중복제거후_active는_건드리지_않고_upsert한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("A1", "삼성전자", "005930"),
                dc("B1", "SK하이닉스", "000660"),
                dc("C1", "비상장회사", null),
                dc("D1", "공백코드", " "),
                dc("A2", "삼성전자우선주표기", "005930")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of("005930"));

        InstrumentSyncReport report = service(500).sync(false);

        ArgumentCaptor<List<DartCorpCode>> chunk = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> active = ArgumentCaptor.forClass(Boolean.class);
        verify(writer).upsertChunk(chunk.capture(), active.capture());
        assertThat(chunk.getValue()).extracting(DartCorpCode::stockCode).containsExactly("005930", "000660");
        assertThat(active.getValue()).isNull(); // 필터 OFF → active 미변경

        assertThat(report.dartTotalCount()).isEqualTo(5);
        assertThat(report.dartListedCount()).isEqualTo(3);
        assertThat(report.dedupedCount()).isEqualTo(2);
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.konexDeactivated()).isZero();
        assertThat(report.corpClsFilterApplied()).isFalse();
        verifyNoInteractions(dartCompanyClient);
    }

    @Test
    void chunkSize로_나눠서_여러_청크로_upsert한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002"), dc("c3", "C", "000003"),
                dc("c4", "D", "000004"), dc("c5", "E", "000005")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());

        InstrumentSyncReport report = service(2).sync(false);

        verify(writer, times(3)).upsertChunk(anyList(), isNull()); // 2 + 2 + 1
        assertThat(report.created()).isEqualTo(5);
    }

    @Test
    void 청크_실패시_건너뛰고_failed로_집계하며_예외는_전파하지_않는다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002"), dc("c3", "C", "000003")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(writer).upsertChunk(anyList(), isNull());

        InstrumentSyncReport report = service(500).sync(false);

        assertThat(report.failed()).isEqualTo(3);
        assertThat(report.created()).isZero();
    }

    // ── corp_cls 필터 ON ─────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void 코넥스_N_종목은_active_false로_그_외는_active_true로_upsert한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("cA", "유가A", "000001"), dc("cB", "코넥스B", "000002"), dc("cC", "코스닥C", "000003")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());
        when(dartCompanyClient.getCompany("cA")).thenReturn(company("Y"));
        when(dartCompanyClient.getCompany("cB")).thenReturn(company("N"));
        when(dartCompanyClient.getCompany("cC")).thenReturn(company("K"));

        InstrumentSyncReport report = service(500).sync(true);

        ArgumentCaptor<List<DartCorpCode>> chunk = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Boolean> active = ArgumentCaptor.forClass(Boolean.class);
        verify(writer, times(2)).upsertChunk(chunk.capture(), active.capture());
        // 두 번의 호출: (활성 [000001,000003], true) 와 (코넥스 [000002], false)
        assertThat(active.getAllValues()).containsExactly(true, false);
        assertThat(chunk.getAllValues().get(0)).extracting(DartCorpCode::stockCode).containsExactly("000001", "000003");
        assertThat(chunk.getAllValues().get(1)).extracting(DartCorpCode::stockCode).containsExactly("000002");

        assertThat(report.corpClsFilterApplied()).isTrue();
        assertThat(report.dedupedCount()).isEqualTo(3);
        assertThat(report.konexDeactivated()).isEqualTo(1); // active=false 로 DB 반영된 코넥스 건수
        assertThat(report.companyLookupFailed()).isZero();
        assertThat(report.created()).isEqualTo(3);
    }

    @Test
    void 기업개황_조회_실패한_종목은_활성으로_분류하고_lookupFailed로_집계한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("cA", "A", "000001"), dc("cB", "B", "000002"), dc("cC", "C", "000003")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());
        when(dartCompanyClient.getCompany("cA")).thenReturn(company("Y"));
        when(dartCompanyClient.getCompany("cB")).thenThrow(new DartApiException("조회된 데이타가 없습니다."));
        when(dartCompanyClient.getCompany("cC")).thenReturn(company("K"));

        InstrumentSyncReport report = service(500).sync(true);

        verify(writer, times(1)).upsertChunk(any(), any()); // 코넥스 없음 → 활성 청크 1회만
        assertThat(report.konexDeactivated()).isZero();
        assertThat(report.companyLookupFailed()).isEqualTo(1);
        assertThat(report.aborted()).isFalse();
        assertThat(report.created()).isEqualTo(3); // 실패한 B 도 활성으로 포함
    }

    @Test
    void 기업개황_조회가_연속_실패하면_필터를_중단하고_나머지는_활성으로_처리한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002"), dc("c3", "C", "000003"),
                dc("c4", "D", "000004"), dc("c5", "E", "000005")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());
        when(dartCompanyClient.getCompany(anyString())).thenThrow(new DartApiException("요청 제한 초과"));

        InstrumentSyncReport report = service(500, 2).sync(true); // 연속 2회 실패 시 중단

        verify(dartCompanyClient, times(2)).getCompany(anyString());
        assertThat(report.aborted()).isTrue();
        assertThat(report.companyLookupFailed()).isEqualTo(2);
        assertThat(report.konexDeactivated()).isZero();
        assertThat(report.created()).isEqualTo(5); // 전부 활성 처리
    }

    @Test
    @SuppressWarnings("unchecked")
    void symbols_지정하면_해당_종목만_대상으로_한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002"), dc("c3", "C", "000003"),
                dc("c4", "D", "000004"), dc("c5", "E", "000005")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());

        InstrumentSyncReport report = service(500).sync(false, List.of("000002", "000004"));

        ArgumentCaptor<List<DartCorpCode>> chunk = ArgumentCaptor.forClass(List.class);
        verify(writer).upsertChunk(chunk.capture(), isNull());
        assertThat(chunk.getValue()).extracting(DartCorpCode::stockCode).containsExactly("000002", "000004");
        assertThat(report.dedupedCount()).isEqualTo(2);
        assertThat(report.created()).isEqualTo(2);
    }

    @Test
    void filterByCorpCls_false면_기업개황_조회를_안하고_active도_null로_전달한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());

        InstrumentSyncReport report = service(500).sync(false);

        verifyNoInteractions(dartCompanyClient);
        verify(writer).upsertChunk(anyList(), isNull());
        assertThat(report.corpClsFilterApplied()).isFalse();
        assertThat(report.created()).isEqualTo(2);
    }

    // ── 상장폐지 감지 (detectDelistings) ─────────────────────────

    @Test
    void detectDelistings_true면_업서트후_stale_KR_STOCK를_비활성화한다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of("000001"));
        when(writer.deactivateStale(any())).thenReturn(4);

        InstrumentSyncReport report = service(500).sync(false, List.of(), true);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(writer).deactivateStale(cutoff.capture());
        // 유예일(3d) 만큼 과거
        Instant expected = Instant.now().minus(STALE_DAYS, ChronoUnit.DAYS);
        assertThat(cutoff.getValue()).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
        assertThat(report.delistingsDeactivated()).isEqualTo(4);
    }

    @Test
    void detectDelistings_true여도_onlySymbols_지정시엔_스윕하지_않는다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());

        InstrumentSyncReport report = service(500).sync(false, List.of("000001"), true);

        verify(writer, never()).deactivateStale(any());
        assertThat(report.delistingsDeactivated()).isZero();
    }

    @Test
    void 청크_실패가_있으면_상장폐지_스윕을_건너뛴다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(
                dc("c1", "A", "000001"), dc("c2", "B", "000002")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());
        doThrow(new RuntimeException("boom")).when(writer).upsertChunk(anyList(), isNull());

        InstrumentSyncReport report = service(500).sync(false, List.of(), true);

        verify(writer, never()).deactivateStale(any());
        assertThat(report.failed()).isEqualTo(2);
        assertThat(report.delistingsDeactivated()).isZero();
    }

    @Test
    void detectDelistings_기본값_false면_스윕하지_않는다() {
        when(dartCorpCodeClient.downloadAll()).thenReturn(List.of(dc("c1", "A", "000001")));
        when(instrumentRepository.findAllSymbols()).thenReturn(List.of());

        service(500).sync(false);          // 1-arg
        service(500).sync(false, List.of()); // 2-arg

        verify(writer, never()).deactivateStale(any());
    }
}
