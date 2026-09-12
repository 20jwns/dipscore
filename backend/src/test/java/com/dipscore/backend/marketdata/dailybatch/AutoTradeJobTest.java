package com.dipscore.backend.marketdata.dailybatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.dipscore.backend.marketdata.dailybatch.AutoTradeJob.AutoTradeReport;
import com.dipscore.backend.marketdata.dailybatch.AutoTradeJob.Status;
import com.dipscore.backend.signal.SignalDecision;
import com.dipscore.backend.signal.SignalException;
import com.dipscore.backend.signal.TradingSignalResult;
import com.dipscore.backend.signal.TradingSignalService;
import com.dipscore.backend.trading.BuyExecutionService;
import com.dipscore.backend.trading.Position;
import com.dipscore.backend.trading.PositionRepository;
import com.dipscore.backend.trading.PositionStatus;
import com.dipscore.backend.trading.TradingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutoTradeJobTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 10:00 KST — 장중(09:00~15:30). */
    private static final Clock DURING_MARKET = Clock.fixed(Instant.parse("2026-09-10T01:00:00Z"), SEOUL);
    /** 08:00 KST — 장 시작 전. */
    private static final Clock BEFORE_MARKET = Clock.fixed(Instant.parse("2026-09-09T23:00:00Z"), SEOUL);
    /** 16:00 KST — 장 마감 후. */
    private static final Clock AFTER_MARKET = Clock.fixed(Instant.parse("2026-09-10T07:00:00Z"), SEOUL);

    @Mock TradingSignalService signalService;
    @Mock BuyExecutionService buyExecutionService;
    @Mock PositionRepository positionRepository;

    private AutoTradeJob job() {
        return job(DURING_MARKET);
    }

    private AutoTradeJob job(Clock clock) {
        return new AutoTradeJob(signalService, buyExecutionService, positionRepository,
                new DailyBatchProperties(List.of("005930", "000660")),
                new AutoTradeProperties(true, "0 5 9 * * MON-FRI", ACCOUNT_ID, "09:00", "15:30"),
                clock);
    }

    private static TradingSignalResult signal(String symbol, SignalDecision decision) {
        return new TradingSignalResult(symbol, Instant.now(), decision, decision.label(),
                75.0, 65.0, 1.0, Instant.now(), Instant.now(), List.of());
    }

    private void noExistingPosition(String symbol) {
        when(positionRepository.findByAccountIdAndSymbolAndStatus(ACCOUNT_ID, symbol, PositionStatus.OPEN))
                .thenReturn(Optional.empty());
    }

    @Test
    void 장중이고_BUY신호면_자동매수한다() {
        when(signalService.evaluateBuySignal("005930", ACCOUNT_ID)).thenReturn(signal("005930", SignalDecision.BUY));
        when(signalService.evaluateBuySignal("000660", ACCOUNT_ID)).thenReturn(signal("000660", SignalDecision.WAIT));
        noExistingPosition("005930");
        Position position = Position.open(ACCOUNT_ID, "005930", 5, BigDecimal.valueOf(1_000), Instant.now(), null);
        when(buyExecutionService.buyIfSignaled(ACCOUNT_ID, "005930")).thenReturn(position);

        AutoTradeReport report = job().run(List.of("005930", "000660"), ACCOUNT_ID, false);

        assertThat(report.marketOpen()).isTrue();
        assertThat(report.buySignals()).isEqualTo(1);
        assertThat(report.bought()).isEqualTo(1);
        assertThat(report.noSignal()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.status()).isEqualTo(Status.BOUGHT);
            assertThat(r.quantity()).isEqualTo(5L);
        });
    }

    @Test
    void 장_시작_전이면_전부_건너뛴다() {
        AutoTradeReport report = job(BEFORE_MARKET).run(List.of("005930", "000660"), ACCOUNT_ID, false);

        assertThat(report.marketOpen()).isFalse();
        assertThat(report.bought()).isZero();
        assertThat(report.requested()).isEqualTo(2);
        verifyNoInteractions(signalService, buyExecutionService, positionRepository);
    }

    @Test
    void 장_마감_후면_전부_건너뛴다() {
        AutoTradeReport report = job(AFTER_MARKET).run(List.of("005930"), ACCOUNT_ID, false);

        assertThat(report.marketOpen()).isFalse();
        assertThat(report.bought()).isZero();
        verifyNoInteractions(signalService, buyExecutionService);
    }

    @Test
    void skipMarketHoursGuard_true면_장외여도_처리한다() {
        when(signalService.evaluateBuySignal("005930", ACCOUNT_ID)).thenReturn(signal("005930", SignalDecision.WAIT));

        AutoTradeReport report = job(BEFORE_MARKET).run(List.of("005930"), ACCOUNT_ID, true);

        assertThat(report.marketOpen()).isFalse(); // 실제로는 장외였지만
        verify(signalService).evaluateBuySignal("005930", ACCOUNT_ID); // 그래도 처리됨
        assertThat(report.noSignal()).isEqualTo(1);
    }

    @Test
    void 이미_보유중이면_BUY신호여도_매수하지_않는다() {
        when(signalService.evaluateBuySignal("005930", ACCOUNT_ID)).thenReturn(signal("005930", SignalDecision.BUY));
        when(positionRepository.findByAccountIdAndSymbolAndStatus(ACCOUNT_ID, "005930", PositionStatus.OPEN))
                .thenReturn(Optional.of(mock(Position.class)));

        AutoTradeReport report = job().run(List.of("005930"), ACCOUNT_ID, false);

        assertThat(report.buySignals()).isEqualTo(1);
        assertThat(report.alreadyHeld()).isEqualTo(1);
        assertThat(report.bought()).isZero();
        verify(buyExecutionService, never()).buyIfSignaled(any(), any());
    }

    @Test
    void 신호판정_실패한_종목은_실패로_집계하고_다음_종목_계속한다() {
        when(signalService.evaluateBuySignal("005930", ACCOUNT_ID))
                .thenThrow(new SignalException("저점 진입 스코어 없음: 005930"));
        when(signalService.evaluateBuySignal("000660", ACCOUNT_ID)).thenReturn(signal("000660", SignalDecision.BUY));
        noExistingPosition("000660");
        Position position = Position.open(ACCOUNT_ID, "000660", 1, BigDecimal.valueOf(1_800_000), Instant.now(), null);
        when(buyExecutionService.buyIfSignaled(ACCOUNT_ID, "000660")).thenReturn(position);

        AutoTradeReport report = job().run(List.of("005930", "000660"), ACCOUNT_ID, false);

        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.bought()).isEqualTo(1);
        assertThat(report.results()).anySatisfy(r -> {
            assertThat(r.symbol()).isEqualTo("005930");
            assertThat(r.status()).isEqualTo(Status.FAILED);
            assertThat(r.error()).contains("저점 진입 스코어 없음");
        });
    }

    @Test
    void 매수체결_실패한_종목은_실패로_집계하고_다음_종목_계속한다() {
        when(signalService.evaluateBuySignal("005930", ACCOUNT_ID)).thenReturn(signal("005930", SignalDecision.BUY));
        noExistingPosition("005930");
        when(buyExecutionService.buyIfSignaled(ACCOUNT_ID, "005930"))
                .thenThrow(new TradingException("ATR 계산 불가(일봉 부족): 005930"));
        when(signalService.evaluateBuySignal("000660", ACCOUNT_ID)).thenReturn(signal("000660", SignalDecision.BUY));
        noExistingPosition("000660");
        Position position = Position.open(ACCOUNT_ID, "000660", 1, BigDecimal.valueOf(1_800_000), Instant.now(), null);
        when(buyExecutionService.buyIfSignaled(ACCOUNT_ID, "000660")).thenReturn(position);

        AutoTradeReport report = job().run(List.of("005930", "000660"), ACCOUNT_ID, false);

        assertThat(report.buySignals()).isEqualTo(2);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.bought()).isEqualTo(1);
    }

    @Test
    void 계좌가_지정되지_않으면_아무것도_하지_않는다() {
        AutoTradeReport report = job().run(List.of("005930"), null, false);

        assertThat(report.bought()).isZero();
        verifyNoInteractions(signalService, buyExecutionService, positionRepository);
    }

    @Test
    void 대상_종목이_없으면_아무것도_하지_않는다() {
        AutoTradeReport report = job().run(List.of(), ACCOUNT_ID, false);

        assertThat(report.requested()).isZero();
        verifyNoInteractions(signalService, buyExecutionService, positionRepository);
    }

    @Test
    void 스케줄된_실행은_계좌ID_미지정시_경고만_남기고_건너뛴다() {
        AutoTradeJob jobNoAccount = new AutoTradeJob(signalService, buyExecutionService, positionRepository,
                new DailyBatchProperties(List.of("005930")),
                new AutoTradeProperties(true, "0 5 9 * * MON-FRI", null, "09:00", "15:30"),
                DURING_MARKET);

        jobNoAccount.scheduledRun(); // 같은 패키지라 직접 호출 가능

        verifyNoInteractions(signalService, buyExecutionService, positionRepository);
    }
}
