package com.dipscore.backend.trading;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가상 계좌 개설/조회. 체결(현금 증감)은 {@link BuyExecutionService}/{@link SellExecutionService} 가 담당.
 *
 * <p>{@code account.enabled=true} 일 때만 빈 생성 (테스트 컨텍스트엔 JPA 가 없어 미생성).
 */
@Service
@ConditionalOnProperty(prefix = "account", name = "enabled", havingValue = "true")
public class AccountService {

    private final AccountRepository accountRepository;
    private final PositionRepository positionRepository;
    private final AccountProperties props;

    public AccountService(AccountRepository accountRepository,
                          PositionRepository positionRepository,
                          AccountProperties props) {
        this.accountRepository = accountRepository;
        this.positionRepository = positionRepository;
        this.props = props;
    }

    /**
     * 신규 계좌 개설. {@code initialCapital} 이 null 이면 설정 기본값({@code account.initial-capital}) 사용.
     *
     * @throws TradingException 초기자금이 {@code account.min-initial-capital} 미만
     */
    @Transactional
    public Account openAccount(String name, BigDecimal initialCapital) {
        BigDecimal capital = initialCapital != null ? initialCapital : props.initialCapital();
        if (capital.compareTo(props.minInitialCapital()) < 0) {
            throw new TradingException(
                    "초기자금은 최소 %s원 이상이어야 합니다: %s".formatted(props.minInitialCapital(), capital));
        }
        Account account = Account.open(
                name == null || name.isBlank() ? "default" : name, capital, props.currency());
        return accountRepository.save(account);
    }

    public Account findOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new TradingException("계좌 없음: " + accountId));
    }

    /** @param status null 이면 전체(OPEN+CLOSED), 지정하면 그 상태만. */
    public List<Position> positions(Long accountId, PositionStatus status) {
        findOrThrow(accountId); // 존재 확인
        return status == null
                ? positionRepository.findByAccountId(accountId)
                : positionRepository.findByAccountIdAndStatus(accountId, status);
    }
}
