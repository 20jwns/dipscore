package com.dipscore.backend.trading;

import java.math.BigDecimal;

import com.dipscore.backend.signal.SellSignalResult;
import com.dipscore.backend.signal.TradingSignalService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매도 신호를 판정하고, 조건이 트리거되면(목표수익률/손절/보유시간 초과) 실제로 가상 매도 체결한다 (기획서 7-2).
 * {@link TradingSignalService#evaluateSellSignal} 로 판정하고, 매도일 때만 {@link OrderExecutor} 로 체결해
 * {@link Position} 을 청산하고 {@link Account} 현금을 회수한다.
 *
 * <p>{@code account.enabled} + {@code signal.enabled} 가 모두 true 일 때만 빈 생성.
 */
@Service
@ConditionalOnProperty(name = {"account.enabled", "signal.enabled"}, havingValue = "true")
public class SellExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SellExecutionService.class);

    private final TradingSignalService signalService;
    private final OrderExecutor orderExecutor;
    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;

    public SellExecutionService(TradingSignalService signalService,
                                OrderExecutor orderExecutor,
                                AccountRepository accountRepository,
                                PositionRepository positionRepository) {
        this.signalService = signalService;
        this.orderExecutor = orderExecutor;
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
    }

    /** 지정 포지션의 매도 신호를 판정하고, 트리거되면 체결·청산한다. 트리거 안 되면 판정 결과만 반환(HOLD). */
    @Transactional
    public SellSignalResult evaluateAndExecute(Long accountId, Long positionId) {
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new TradingException("포지션 없음: " + positionId));
        if (!position.getAccountId().equals(accountId)) {
            throw new TradingException("계좌 %d 의 포지션이 아닙니다: position=%d".formatted(accountId, positionId));
        }

        SellSignalResult signal = signalService.evaluateSellSignal(position);
        if (!signal.sell()) {
            log.info("[sell-execution] 계좌 {} 포지션 {}({}): HOLD (return={})",
                    accountId, positionId, position.getSymbol(), signal.returnRate());
            return signal;
        }

        OrderExecution exec = orderExecutor.sellMarket(position.getSymbol(), position.getQuantity());
        position.close(exec.executedPrice(), exec.executedAt(), signal.reason());

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TradingException("계좌 없음: " + accountId));
        BigDecimal proceeds = exec.executedPrice().multiply(BigDecimal.valueOf(position.getQuantity()));
        account.credit(proceeds);

        log.info("[sell-execution] 계좌 {} 포지션 {}({}): {} 청산 {}주 @ {} (실현손익 {}, 잔고 {})",
                accountId, positionId, position.getSymbol(), signal.reason(),
                position.getQuantity(), exec.executedPrice(), position.getRealizedPnl(), account.getCashBalance());
        return signal;
    }
}
