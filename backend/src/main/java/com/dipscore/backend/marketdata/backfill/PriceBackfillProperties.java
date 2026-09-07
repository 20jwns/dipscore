package com.dipscore.backend.marketdata.backfill;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 과거 시세 백필 설정 ({@code backfill.prices.*}).
 * {@code enabled=true} 여도 자동 실행은 없다 — 엔드포인트({@code POST /api/admin/price-backfill})로만 수동 트리거.
 * (테스트 컨텍스트엔 이 키가 없어 관련 빈이 아예 생성되지 않는다.)
 */
@ConfigurationProperties(prefix = "backfill.prices")
public record PriceBackfillProperties(

        @DefaultValue("false") boolean enabled,

        @DefaultValue({"005930", "000660", "042700"}) List<String> symbols,

        /** 최근 며칠(달력일) 치를 받을지. 365 ≈ 250 영업일. */
        @DefaultValue("365") int days,

        /** 캔들 API 호출 사이 딜레이 (rate limit 완화). 종목 간/페이지 간 공통 적용. */
        @DefaultValue("300") long requestDelayMs
) {}
