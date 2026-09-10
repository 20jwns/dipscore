package com.dipscore.backend.trading;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 가상 계좌/포지션 설정 ({@code account.*}).
 * {@code enabled=true} 일 때만 DB 의존 빈(AccountService/BuyExecutionService/SellExecutionService/
 * MockOrderExecutor/컨트롤러) 이 생성된다 (테스트 컨텍스트엔 이 키가 없어 미생성).
 */
@ConfigurationProperties(prefix = "account")
public record AccountProperties(

        @DefaultValue("false") boolean enabled,

        /** {@code POST /api/account/init} 호출 시 initialCapital 미지정이면 쓰는 기본 초기자금. */
        @DefaultValue("10000000") BigDecimal initialCapital,

        /** 계좌 개설 최소 초기자금 — 미달이면 {@code POST /api/account/init} 거부. */
        @DefaultValue("100000") BigDecimal minInitialCapital,

        /** 최소매수단위 — 보유현금이 이 값 이하이면 매수 신호 조건 미충족(기획서 7-1). */
        @DefaultValue("100000") BigDecimal minBuyUnit,

        /**
         * 변동성 기반 포지션 사이징의 거래당 리스크 허용 비율 (계좌 현재 잔고 대비, 예: 0.01 = 1%).
         * 매수 수량 = (잔고 × 이 값) / (매수가 - 손절가), 손절가는 {@code position.entry_atr} ×
         * {@code signal.stop-loss-atr-multiple} 재사용 — {@link BuyExecutionService} 참고.
         */
        @DefaultValue("0.01") BigDecimal riskPerTradePct,

        @DefaultValue("KRW") String currency
) {}
