package com.dipscore.backend.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock PositionRepository positionRepository;

    private AccountService service() {
        return new AccountService(accountRepository, positionRepository,
                new AccountProperties(true, BigDecimal.valueOf(10_000_000),
                        BigDecimal.valueOf(100_000), BigDecimal.valueOf(100_000),
                        BigDecimal.valueOf(0.01), "KRW"));
    }

    @Test
    void 초기자금_지정하면_그대로_계좌를_개설한다() {
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account account = service().openAccount("테스트계좌", BigDecimal.valueOf(5_000_000));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getInitialCapital()).isEqualByComparingTo("5000000");
        assertThat(captor.getValue().getCashBalance()).isEqualByComparingTo("5000000");
        assertThat(account.getName()).isEqualTo("테스트계좌");
    }

    @Test
    void 초기자금_미지정이면_설정_기본값을_쓴다() {
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account account = service().openAccount(null, null);

        assertThat(account.getInitialCapital()).isEqualByComparingTo("10000000");
        assertThat(account.getName()).isEqualTo("default");
    }

    @Test
    void 초기자금이_최소금액_미만이면_예외이고_저장하지_않는다() {
        assertThatThrownBy(() -> service().openAccount("테스트계좌", BigDecimal.valueOf(50_000)))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("최소");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void 초기자금이_최소금액과_정확히_같으면_개설된다() {
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Account account = service().openAccount("테스트계좌", BigDecimal.valueOf(100_000));

        assertThat(account.getInitialCapital()).isEqualByComparingTo("100000");
    }

    @Test
    void 계좌_없으면_positions_조회시_예외() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().positions(99L, null))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("계좌 없음");
    }

    @Test
    void status지정하면_그_상태만_조회한다() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(org.mockito.Mockito.mock(Account.class)));
        when(positionRepository.findByAccountIdAndStatus(1L, PositionStatus.OPEN))
                .thenReturn(List.of(org.mockito.Mockito.mock(Position.class)));

        List<Position> result = service().positions(1L, PositionStatus.OPEN);

        assertThat(result).hasSize(1);
    }
}
