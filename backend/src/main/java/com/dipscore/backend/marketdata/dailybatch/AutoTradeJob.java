package com.dipscore.backend.marketdata.dailybatch;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.dipscore.backend.signal.SignalDecision;
import com.dipscore.backend.signal.TradingSignalResult;
import com.dipscore.backend.signal.TradingSignalService;
import com.dipscore.backend.trading.BuyExecutionService;
import com.dipscore.backend.trading.Position;
import com.dipscore.backend.trading.PositionRepository;
import com.dipscore.backend.trading.PositionStatus;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "AI 모의투자"의 핵심 연결 고리 — {@code daily-scoring}(08:30) 이 계산해 둔 점수로 만들어지는
 * <b>매수 신호를 실제(가상) 매수 체결로 자동 연결</b>한다.
 *
 * <ul>
 *   <li>스코어 계산(daily-scoring, 08:30)과 매수 체결(이 잡, 기본 09:05)은 <b>분리</b> — 장 시작 후 별도 실행.</li>
 *   <li>실행 시점에 장중 시간대({@code market-open-time}~{@code market-close-time}, 기본 09:00~15:30)인지
 *       가드 체크 — 아니면 스킵(수동 트리거는 파라미터로 우회 가능).</li>
 *   <li>종목별 독립 — BUY 신호 판정 → 이미 보유 중이면 skip → {@link BuyExecutionService#buyIfSignaled}
 *       호출. 한 종목 실패해도 다음 종목 계속.</li>
 * </ul>
 *
 * <p>{@code account.enabled} + {@code signal.enabled} + {@code daily-batch.auto-trade.enabled} 가
 * 모두 true 일 때만 빈 생성. 수동 트리거: {@code POST /api/admin/auto-trade}.
 */
@Component
@ConditionalOnProperty(
        name = {"account.enabled", "signal.enabled", "daily-batch.auto-trade.enabled"},
        havingValue = "true")
public class AutoTradeJob {

    private static final Logger log = LoggerFactory.getLogger(AutoTradeJob.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TradingSignalService signalService;
    private final BuyExecutionService buyExecutionService;
    private final PositionRepository positionRepository;
    private final DailyBatchProperties batchProps;
    private final AutoTradeProperties props;
    private final Clock clock;

    @Autowired
    public AutoTradeJob(TradingSignalService signalService,
                        BuyExecutionService buyExecutionService,
                        PositionRepository positionRepository,
                        DailyBatchProperties batchProps,
                        AutoTradeProperties props) {
        this(signalService, buyExecutionService, positionRepository, batchProps, props, Clock.system(SEOUL));
    }

    /** 커스텀 {@link Clock} 주입용 (장중 가드 테스트). */
    AutoTradeJob(TradingSignalService signalService,
                BuyExecutionService buyExecutionService,
                PositionRepository positionRepository,
                DailyBatchProperties batchProps,
                AutoTradeProperties props,
                Clock clock) {
        this.signalService = signalService;
        this.buyExecutionService = buyExecutionService;
        this.positionRepository = positionRepository;
        this.batchProps = batchProps;
        this.props = props;
        this.clock = clock;
    }

    @PostConstruct
    void logSchedule() {
        log.info("[auto-trade] 스케줄 등록: cron='{}' (Asia/Seoul), 계좌 {}, 대상 {}종목, 장중 시간대 {}~{}",
                props.cron(), props.accountId(), batchProps.symbols().size(),
                props.marketOpenTime(), props.marketCloseTime());
    }

    @Scheduled(cron = "${daily-batch.auto-trade.cron:0 5 9 * * MON-FRI}", zone = "Asia/Seoul")
    void scheduledRun() {
        if (props.accountId() == null) {
            log.warn("[auto-trade] daily-batch.auto-trade.account-id 미지정 — 건너뜀");
            return;
        }
        run(batchProps.symbols(), props.accountId(), false);
    }

    /**
     * 지정 종목의 매수 신호를 판정하고, BUY 이면서 미보유면 자동 체결한다.
     *
     * @param skipMarketHoursGuard true 면 장중 시간대 체크를 건너뜀 (수동 테스트용)
     */
    public AutoTradeReport run(List<String> symbols, Long accountId, boolean skipMarketHoursGuard) {
        boolean marketOpen = isMarketOpen();

        if (symbols == null || symbols.isEmpty()) {
            log.warn("[auto-trade] 대상 종목이 없어 건너뜀");
            return new AutoTradeReport(0, 0, 0, 0, 0, 0, marketOpen, List.of());
        }
        if (accountId == null) {
            log.warn("[auto-trade] 계좌가 지정되지 않아 건너뜀");
            return new AutoTradeReport(symbols.size(), 0, 0, 0, 0, 0, marketOpen, List.of());
        }
        if (!skipMarketHoursGuard && !marketOpen) {
            log.info("[auto-trade] 장중 시간대({}~{}, Asia/Seoul) 아님 — 매수 체결 건너뜀 (현재 {})",
                    props.marketOpenTime(), props.marketCloseTime(), LocalTime.now(clock));
            return new AutoTradeReport(symbols.size(), 0, 0, 0, 0, 0, false, List.of());
        }

        List<SymbolResult> results = new ArrayList<>(symbols.size());
        int buySignals = 0;
        int bought = 0;
        int alreadyHeld = 0;
        int noSignal = 0;
        int failed = 0;

        for (String symbol : symbols) {
            TradingSignalResult signal;
            try {
                signal = signalService.evaluateBuySignal(symbol, accountId);
            } catch (RuntimeException e) {
                failed++;
                results.add(new SymbolResult(symbol, Status.FAILED, null, null, null, "신호판정 실패: " + e));
                log.warn("[auto-trade] {}: 신호 판정 실패, 다음 종목으로: {}", symbol, e.toString());
                continue;
            }

            if (signal.decision() != SignalDecision.BUY) {
                noSignal++;
                results.add(new SymbolResult(symbol, Status.NO_SIGNAL, null, null, null, null));
                continue;
            }
            buySignals++;

            if (positionRepository.findByAccountIdAndSymbolAndStatus(accountId, symbol, PositionStatus.OPEN)
                    .isPresent()) {
                alreadyHeld++;
                results.add(new SymbolResult(symbol, Status.ALREADY_HELD, null, null, null, null));
                log.info("[auto-trade] {}: BUY 신호이나 이미 보유 중 — 건너뜀", symbol);
                continue;
            }

            try {
                Position position = buyExecutionService.buyIfSignaled(accountId, symbol);
                bought++;
                results.add(new SymbolResult(symbol, Status.BOUGHT, position.getId(), position.getQuantity(),
                        position.getEntryPrice(), null));
                log.info("[auto-trade] {}: BUY 신호 → 자동 매수 체결 {}주 @ {} (position={})",
                        symbol, position.getQuantity(), position.getEntryPrice(), position.getId());
            } catch (RuntimeException e) {
                failed++;
                results.add(new SymbolResult(symbol, Status.FAILED, null, null, null, "매수체결 실패: " + e));
                log.warn("[auto-trade] {}: BUY 신호였으나 체결 실패, 다음 종목으로: {}", symbol, e.toString());
            }
        }

        log.info("[auto-trade] 완료: {}종목 중 BUY신호 {} (매수체결 {} / 이미보유 {} / 실패 {}), 신호없음 {}",
                symbols.size(), buySignals, bought, alreadyHeld, failed, noSignal);
        return new AutoTradeReport(symbols.size(), buySignals, bought, alreadyHeld, noSignal, failed,
                marketOpen, results);
    }

    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now(clock);
        LocalTime open = LocalTime.parse(props.marketOpenTime());
        LocalTime close = LocalTime.parse(props.marketCloseTime());
        return !now.isBefore(open) && now.isBefore(close);
    }

    public enum Status {
        /** BUY 신호 아님(WAIT). */
        NO_SIGNAL,
        /** BUY 신호이나 이미 보유 중이라 건너뜀. */
        ALREADY_HELD,
        /** BUY 신호로 자동 매수 체결됨. */
        BOUGHT,
        /** 신호 판정 또는 매수 체결 실패. */
        FAILED
    }

    /**
     * @param positionId  체결로 생성된 포지션 ID (BOUGHT 일 때만)
     * @param quantity    체결 수량 (BOUGHT 일 때만)
     * @param entryPrice  체결가 (BOUGHT 일 때만)
     * @param error       실패 사유 (FAILED 일 때만)
     */
    public record SymbolResult(String symbol, Status status, Long positionId, Long quantity,
                               BigDecimal entryPrice, String error) {}

    /**
     * @param requested   요청 종목 수
     * @param buySignals  BUY 로 판정된 종목 수 (매수체결+이미보유+체결실패 포함, 신호판정 자체 실패는 제외)
     * @param bought      실제로 자동 매수 체결된 수
     * @param alreadyHeld BUY 신호였으나 이미 보유 중이라 건너뛴 수
     * @param noSignal    WAIT(매수 신호 아님) 수
     * @param failed      신호판정 또는 체결 실패 수
     * @param marketOpen  실행 시점이 장중 시간대였는지
     */
    public record AutoTradeReport(int requested, int buySignals, int bought, int alreadyHeld,
                                  int noSignal, int failed, boolean marketOpen, List<SymbolResult> results) {}
}
