package com.dipscore.backend.web;

import java.math.BigDecimal;
import java.util.List;

import com.dipscore.backend.signal.SellSignalResult;
import com.dipscore.backend.trading.Account;
import com.dipscore.backend.trading.AccountService;
import com.dipscore.backend.trading.AccountView;
import com.dipscore.backend.trading.BuyExecutionService;
import com.dipscore.backend.trading.Position;
import com.dipscore.backend.trading.PositionStatus;
import com.dipscore.backend.trading.PositionView;
import com.dipscore.backend.trading.SellExecutionService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가상 계좌/포지션 스모크 테스트용 엔드포인트.
 * ({@code account.enabled} + {@code signal.enabled} 가 모두 true 일 때만 등록)
 *
 * <p>{@code POST /api/account/init} — 계좌 생성.
 * <br>{@code POST /api/account/{accountId}/buy/{symbol}} — 매수 신호 판정 후 BUY 면 체결 (수동 테스트용).
 * <br>{@code POST /api/account/{accountId}/positions/{positionId}/sell-check} — 매도 신호 판정, 트리거되면 체결.
 * <br>{@code GET /api/account/{accountId}/positions} — 보유 포지션 조회 (?status=OPEN|CLOSED).
 */
@RestController
@RequestMapping("/api/account")
@ConditionalOnProperty(name = {"account.enabled", "signal.enabled"}, havingValue = "true")
public class AccountController {

    private final AccountService accountService;
    private final BuyExecutionService buyExecutionService;
    private final SellExecutionService sellExecutionService;

    public AccountController(AccountService accountService,
                             BuyExecutionService buyExecutionService,
                             SellExecutionService sellExecutionService) {
        this.accountService = accountService;
        this.buyExecutionService = buyExecutionService;
        this.sellExecutionService = sellExecutionService;
    }

    /**
     * 예: {@code POST /api/account/init?name=테스트계좌&initialCapital=10000000}. 둘 다 생략 가능(설정 기본값).
     * {@code initialCapital} 이 {@code account.min-initial-capital}(기본 10만원) 미만이면 422.
     */
    @PostMapping("/init")
    public AccountView init(@RequestParam(required = false) String name,
                            @RequestParam(required = false) BigDecimal initialCapital) {
        Account account = accountService.openAccount(name, initialCapital);
        return AccountView.of(account);
    }

    /** 매수 신호 판정 후 BUY 면 체결. WAIT 이면 422(어떤 조건이 미달인지 메시지에 포함). */
    @PostMapping("/{accountId}/buy/{symbol}")
    public PositionView buy(@PathVariable Long accountId, @PathVariable String symbol) {
        Position position = buyExecutionService.buyIfSignaled(accountId, symbol);
        return PositionView.of(position);
    }

    /** 매도 신호 판정, 트리거되면(목표수익률/손절/보유시간초과) 체결. 아니면 판정 결과만(HOLD) 반환. */
    @PostMapping("/{accountId}/positions/{positionId}/sell-check")
    public SellSignalResult sellCheck(@PathVariable Long accountId, @PathVariable Long positionId) {
        return sellExecutionService.evaluateAndExecute(accountId, positionId);
    }

    /** 보유 포지션 조회. {@code ?status=OPEN} 또는 {@code ?status=CLOSED} 로 필터, 생략하면 전체. */
    @GetMapping("/{accountId}/positions")
    public List<PositionView> positions(@PathVariable Long accountId,
                                        @RequestParam(required = false) PositionStatus status) {
        return accountService.positions(accountId, status).stream().map(PositionView::of).toList();
    }
}
