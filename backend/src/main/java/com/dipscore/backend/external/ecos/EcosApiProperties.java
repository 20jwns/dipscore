package com.dipscore.backend.external.ecos;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 한국은행 ECOS 오픈API 설정 ({@code ecos.api.*}). 실제 키는 {@code backend/.env} 의
 * {@code ECOS_API_KEY} 로 주입한다 (커밋 금지, ecos.bok.or.kr/api 에서 발급).
 *
 * <p>ECOS 는 인증키를 <b>URL 경로 세그먼트</b>로 전달한다:
 * {@code /api/StatisticSearch/{KEY}/json/{lang}/{start}/{end}/{statCode}/{cycle}/{startTime}/{endTime}/{itemCode}}
 *
 * <p>통계표코드/항목코드는 2026-09 ECOS 실데이터로 검증한 값 (StatisticSearch, sample 키):
 * <ul>
 *   <li>기준금리 - 722Y001 / 0101000 / 주기 M (단위 연%)</li>
 *   <li>원/달러 환율(매매기준율) - 731Y001 / 0000001 / 주기 D (단위 원)</li>
 *   <li>소비자물가지수(총지수) - 901Y009 / 0 / 주기 M (2020=100)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "ecos.api")
public record EcosApiProperties(

        @DefaultValue("") String apiKey,

        @DefaultValue("https://ecos.bok.or.kr") String baseUrl,

        @DefaultValue("3s") Duration connectTimeout,

        @DefaultValue("10s") Duration readTimeout,

        /** 응답 언어 (kr/en). */
        @DefaultValue("kr") String language,

        @DefaultValue("/api/StatisticSearch") String statisticSearchPath,

        /** 1회 조회 최대 건수 (ECOS 정식키는 최대 100000, sample 키는 10). */
        @DefaultValue("100") int maxRows,

        @DefaultValue Indicators indicators
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 지표별 통계표/항목 코드 묶음. 값은 {@code application.yml} 에서 주입 (튜닝 용이하게 분리). */
    public record Indicators(
            @DefaultValue Indicator baseRate,
            @DefaultValue Indicator exchangeRate,
            @DefaultValue Indicator cpi
    ) {}

    /**
     * @param statCode 통계표코드 (예: 722Y001)
     * @param itemCode 통계항목코드1 (예: 0101000)
     * @param cycle    주기 D(일) / M(월) / Q(분기) / A(연)
     */
    public record Indicator(String statCode, String itemCode, String cycle) {}
}
