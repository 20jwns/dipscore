package com.dipscore.backend.attractiveness.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.financials.DartFinancialsClient;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementItem;
import com.dipscore.backend.external.dart.financials.dto.DartFinancialStatementResponse;
import com.dipscore.backend.external.dart.stocktotal.DartStockTotalCountClient;
import com.dipscore.backend.external.dart.stocktotal.dto.DartStockTotalCountItem;
import com.dipscore.backend.external.dart.stocktotal.dto.DartStockTotalCountResponse;
import com.dipscore.backend.marketdata.financial.FinancialSnapshot;
import com.dipscore.backend.marketdata.financial.FinancialSnapshotRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DartFinancialSnapshotIngestionServiceTest {

    @Mock
    DartFinancialsClient financialsClient;
    @Mock
    DartStockTotalCountClient stockTotalCountClient;
    @Mock
    FinancialSnapshotRepository repository;

    private DartFinancialSnapshotIngestionService service() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new DartFinancialSnapshotIngestionService(financialsClient, stockTotalCountClient, repository);
    }

    private static DartFinancialStatementItem item(String sjDiv, String accountId, String accountName,
                                                   String thstrmAmount, String frmtrmAmount) {
        return new DartFinancialStatementItem("20260311000123", "2025", "00126380",
                sjDiv, sjDiv, accountId, accountName, null,
                "당기", thstrmAmount, "전기", frmtrmAmount, "전전기", null, "1", "KRW");
    }

    /** account_id 없이 이름만 있는(폴백 매칭용) IS/BS 한 벌. 단위: 원. */
    private static List<DartFinancialStatementItem> namedStatements() {
        return List.of(
                item("IS", null, "매출액", "2000000", "1800000"),
                item("IS", null, "영업이익", "500000", null),
                item("IS", null, "당기순이익", "400000", null),
                item("BS", null, "자본총계", "3000000", null),
                item("BS", null, "부채총계", "1000000", null));
    }

    private static DartStockTotalCountResponse shares(String kind, String istcTotqy) {
        return new DartStockTotalCountResponse("000", "정상", List.of(
                new DartStockTotalCountItem("20260311000123", "00126380", kind,
                        "20,000,000,000", null, istcTotqy, "0", istcTotqy)));
    }

    private static DartFinancialStatementResponse ok(List<DartFinancialStatementItem> list) {
        return new DartFinancialStatementResponse("000", "정상", list);
    }

    @Test
    void account_id로_계정을_매칭하며_잘못된_이름의_행에_속지_않는다() {
        List<DartFinancialStatementItem> items = List.of(
                item("IS", "ifrs-full_Revenue", "엉뚱한이름", "1000000", "900000"),
                item("IS", null, "매출액", "9999999", "8888888"), // 이름은 맞지만 account_id 매칭이 우선
                item("IS", "dart_OperatingIncomeLoss", "x", "500000", null),
                item("IS", "ifrs-full_ProfitLoss", "y", "400000", null),
                item("BS", "ifrs-full_Equity", "z", "3000000", null),
                item("BS", "ifrs-full_Liabilities", "w", "1000000", null));
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenReturn(ok(items));
        when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(shares("보통주", "1,000"));

        FinancialSnapshot saved = service().ingest("005930", "00126380", 2025, "CFS");

        // 원 → 백만원
        assertThat(saved.getRevenue()).isEqualByComparingTo("1");          // account_id 행(1,000,000원)
        assertThat(saved.getPriorRevenue()).isEqualByComparingTo("0.9");   // 같은 행의 전기
        assertThat(saved.getOperatingIncome()).isEqualByComparingTo("0.5");
        assertThat(saved.getNetIncome()).isEqualByComparingTo("0.4");
        assertThat(saved.getTotalEquity()).isEqualByComparingTo("3");
        assertThat(saved.getTotalLiabilities()).isEqualByComparingTo("1");
        assertThat(saved.getSharesOutstanding()).isEqualTo(1_000L);
        assertThat(saved.getSource()).isEqualTo("DART_11011_2025");
    }

    @Test
    void 손익계정이_CIS_포괄손익계산서에만_있어도_매칭한다() {
        // SK하이닉스처럼 별도 IS 없이 CIS 만 내는 회사
        List<DartFinancialStatementItem> items = List.of(
                item("CIS", "ifrs-full_Revenue", "매출액", "97146675000000", "66192929000000"),
                item("CIS", "dart_OperatingIncomeLoss", "영업이익(손실)", "47206319000000", null),
                item("CIS", "ifrs-full_ProfitLoss", "당기순이익(손실)", "42947902000000", null),
                item("CIS", "ifrs-full_ComprehensiveIncome", "총포괄이익(손실)", "43017348000000", null),
                item("BS", "ifrs-full_Equity", "자본총계", "120666751000000", null),
                item("BS", "ifrs-full_Liabilities", "부채총계", "55440908000000", null));
        when(financialsClient.getSingleCompanyFullStatements("00164779", 2025, "11011", "CFS"))
                .thenReturn(ok(items));
        lenient().when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(shares("보통주", "728,002,365"));

        FinancialSnapshot saved = service().ingest("000660", "00164779", 2025, "CFS");

        assertThat(saved.getRevenue()).isEqualByComparingTo("97146675");
        assertThat(saved.getOperatingIncome()).isEqualByComparingTo("47206319");
        assertThat(saved.getNetIncome()).isEqualByComparingTo("42947902"); // 총포괄이익 아님
        assertThat(saved.getPriorRevenue()).isEqualByComparingTo("66192929");
        assertThat(saved.getTotalEquity()).isEqualByComparingTo("120666751");
    }

    @Test
    void account_id가_없으면_account_nm으로_폴백_매칭한다() {
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenReturn(ok(namedStatements()));
        when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(shares("보통주", "5,969,782,550"));

        FinancialSnapshot saved = service().ingest("005930", "00126380", 2025, "CFS");

        assertThat(saved.getRevenue()).isEqualByComparingTo("2");
        assertThat(saved.getNetIncome()).isEqualByComparingTo("0.4");
        assertThat(saved.getSharesOutstanding()).isEqualTo(5_969_782_550L);
    }

    @Test
    void 주식총수현황_조회_실패해도_스냅샷은_저장하고_주식수는_null() {
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenReturn(ok(namedStatements()));
        when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenThrow(new DartApiException("주식총수현황 조회 실패: HTTP 500"));

        FinancialSnapshot saved = service().ingest("005930", "00126380", 2025, "CFS");

        assertThat(saved.getSharesOutstanding()).isNull();
        assertThat(saved.getRevenue()).isEqualByComparingTo("2"); // 나머지는 정상 저장
    }

    @Test
    void disclosed_at을_rcept_no_앞8자리에서_파싱한다() {
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenReturn(ok(namedStatements()));
        lenient().when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(shares("보통주", "1,000"));

        FinancialSnapshot saved = service().ingest("005930", "00126380", 2025, "CFS");

        assertThat(saved.getDisclosedAt()).isEqualTo(LocalDate.of(2026, 3, 11));
    }

    @Test
    void ingestLatest는_사업보고서가_없으면_분기보고서로_폴백한다() {
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenThrow(new DartApiException("재무제표 조회 실패 (status=013): 조회된 데이타가 없습니다."));
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11014", "CFS"))
                .thenReturn(ok(namedStatements()));
        lenient().when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(shares("보통주", "1,000"));

        FinancialSnapshot saved = service().ingestLatest("005930", "00126380", 2025, "CFS");

        assertThat(saved.getSource()).isEqualTo("DART_11014_2025"); // 3분기 보고서
        assertThat(saved.getRevenue()).isEqualByComparingTo("2");
    }

    @Test
    void ingestLatest는_모든_보고서에_데이터가_없으면_예외이고_저장하지_않는다() {
        when(financialsClient.getSingleCompanyFullStatements(eq("00126380"), eq(2025), any(), eq("CFS")))
                .thenThrow(new DartApiException("재무제표 조회 실패 (status=013): 조회된 데이타가 없습니다."));

        DartFinancialSnapshotIngestionService svc =
                new DartFinancialSnapshotIngestionService(financialsClient, stockTotalCountClient, repository);

        assertThatThrownBy(() -> svc.ingestLatest("005930", "00126380", 2025, "CFS"))
                .isInstanceOf(DartApiException.class)
                .hasMessageContaining("013");
        verify(repository, never()).save(any());
    }

    @Test
    void account_id_행이_있어도_보통주_행이_없으면_합계_행에서_주식수를_읽는다() {
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenReturn(ok(namedStatements()));
        when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(new DartStockTotalCountResponse("000", "정상", List.of(
                        new DartStockTotalCountItem("r", "00126380", "우선주", null, null, "800,000", "0", "800,000"),
                        new DartStockTotalCountItem("r", "00126380", "합계", null, null, "6,000,000", "0", "6,000,000"))));

        FinancialSnapshot saved = service().ingest("005930", "00126380", 2025, "CFS");

        assertThat(saved.getSharesOutstanding()).isEqualTo(6_000_000L);
    }

    @Test
    void 백만원_단위로_변환해_저장한다() {
        when(financialsClient.getSingleCompanyFullStatements("00126380", 2025, "11011", "CFS"))
                .thenReturn(ok(List.of(item("IS", "ifrs-full_Revenue", "매출액", "300870903000000", null))));
        lenient().when(stockTotalCountClient.getStockTotalCount(any(), anyInt(), any()))
                .thenReturn(shares("보통주", "1,000"));

        ArgumentCaptor<FinancialSnapshot> captor = ArgumentCaptor.forClass(FinancialSnapshot.class);
        service().ingest("005930", "00126380", 2025, "CFS");
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getRevenue()).isEqualByComparingTo("300870903"); // 300.87조원 → 백만원
    }
}
