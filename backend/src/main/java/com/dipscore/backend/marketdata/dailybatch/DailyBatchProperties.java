package com.dipscore.backend.marketdata.dailybatch;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 일별 배치 공통 설정 ({@code daily-batch.*}).
 *
 * <p>{@code symbols} 는 <b>여러 일별 배치가 공유</b>하는 대상 종목코드다
 * (현재는 최신 일봉 적재 {@link DailyCandleSyncJob}, 이후 추가되는 장마감 배치도 이 값을 쓴다).
 */
@ConfigurationProperties(prefix = "daily-batch")
public record DailyBatchProperties(

        /** 일별 배치 대상 종목코드. comma-separated 로 주입. */
        @DefaultValue({"005930", "000660", "042700"}) List<String> symbols
) {}
