package com.dipscore.backend.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dipscore.backend.signal.ExitReason;
import com.dipscore.backend.signal.SellSignalResult;
import com.dipscore.backend.signal.TradingSignalService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellExecutionServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long POSITION_ID = 10L;
    private static final String SYMBOL = "005930";

    @Mock TradingSignalService signalService;
    @Mock OrderExecutor orderExecutor;
    @Mock AccountRepository accountRepository;
    @Mock PositionRepository positionRepository;
    @Mock Account account;

    private SellExecutionService service() {
        return new SellExecutionService(signalService, orderExecutor, accountRepository, positionRepository);
    }

    private static Position openPosition() {
        return Position.open(ACCOUNT_ID, SYMBOL, 10, BigDecimal.valueOf(10_000), Instant.now(), null);
    }

    private static SellSignalResult signal(boolean sell, ExitReason reason) {
        return new SellSignalResult(POSITION_ID, SYMBOL, Instant.now(), sell, reason,
                10_300.0, 0.03, null, 10_250.0, List.of());
    }

    @Test
    void 매도신호가_트리거되면_체결하고_포지션을_청산하고_현금을_회수한다() {
        Position position = openPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(position));
        when(signalService.evaluateSellSignal(position)).thenReturn(signal(true, ExitReason.TARGET_PROFIT));
        when(orderExecutor.sellMarket(SYMBOL, 10))
                .thenReturn(new OrderExecution(SYMBOL, 10, BigDecimal.valueOf(10_300), Instant.now()));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        SellSignalResult result = service().evaluateAndExecute(ACCOUNT_ID, POSITION_ID);

        assertThat(result.sell()).isTrue();
        assertThat(position.getStatus()).isEqualTo(PositionStatus.CLOSED);
        assertThat(position.getExitReason()).isEqualTo(ExitReason.TARGET_PROFIT);
        assertThat(position.getRealizedPnl()).isEqualByComparingTo("3000"); // (10300-10000)*10
        verify(account).credit(BigDecimal.valueOf(103_000));
    }

    @Test
    void 매도신호가_HOLD이면_체결하지_않고_포지션은_그대로다() {
        Position position = openPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(position));
        when(signalService.evaluateSellSignal(position)).thenReturn(signal(false, null));

        SellSignalResult result = service().evaluateAndExecute(ACCOUNT_ID, POSITION_ID);

        assertThat(result.sell()).isFalse();
        assertThat(position.isOpen()).isTrue();
        verify(orderExecutor, never()).sellMarket(any(), org.mockito.ArgumentMatchers.anyLong());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void 포지션이_없으면_예외() {
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().evaluateAndExecute(ACCOUNT_ID, POSITION_ID))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("포지션 없음");
    }

    @Test
    void 다른_계좌의_포지션이면_예외() {
        Position position = Position.open(999L, SYMBOL, 10, BigDecimal.valueOf(10_000), Instant.now(), null);
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> service().evaluateAndExecute(ACCOUNT_ID, POSITION_ID))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("포지션이 아닙니다");
    }

    @Test
    void 매도체결시_계좌가_없으면_예외() {
        Position position = openPosition();
        when(positionRepository.findById(POSITION_ID)).thenReturn(Optional.of(position));
        when(signalService.evaluateSellSignal(position)).thenReturn(signal(true, ExitReason.STOP_LOSS));
        when(orderExecutor.sellMarket(SYMBOL, 10))
                .thenReturn(new OrderExecution(SYMBOL, 10, BigDecimal.valueOf(9_800), Instant.now()));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().evaluateAndExecute(ACCOUNT_ID, POSITION_ID))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("계좌 없음");
    }
}
