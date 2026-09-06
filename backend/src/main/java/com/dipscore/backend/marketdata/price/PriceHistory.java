package com.dipscore.backend.marketdata.price;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 시세 히스토리 1행. TimescaleDB 하이퍼테이블({@code price_history}, 파티션 컬럼 {@code ts}).
 *
 * <p>OHLCV 스키마이지만 현재 적재기({@code PriceIngestionJob})는 토스 {@code /api/v1/prices} 의
 * 현재가(lastPrice)만 얻을 수 있어 {@code close} 만 채우고 open/high/low/volume 은 null 로 둔다.
 * 정규 분봉/일봉 소스가 붙으면 같은 테이블에 전체 필드를 채워 넣는다 ({@code source} 로 출처 구분).
 */
@Entity
@Table(name = "price_history")
@IdClass(PriceHistoryId.class)
public class PriceHistory {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "open", precision = 18, scale = 4)
    private BigDecimal open;

    @Column(name = "high", precision = 18, scale = 4)
    private BigDecimal high;

    @Column(name = "low", precision = 18, scale = 4)
    private BigDecimal low;

    @Column(name = "close", precision = 18, scale = 4, nullable = false)
    private BigDecimal close;

    @Column(name = "volume")
    private Long volume;

    /** 출처 태그 (예: {@code TOSS_QUOTE}, 추후 {@code TOSS_CANDLE_1D} 등). */
    @Column(name = "source", length = 32, nullable = false)
    private String source;

    protected PriceHistory() {
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getTs() {
        return ts;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public Long getVolume() {
        return volume;
    }

    public String getSource() {
        return source;
    }
}
