package com.dipscore.backend.external.dart;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DART 전자공시 오픈API 설정 ({@code dart.api.*}). 값은 {@code application.yml} 에서 바인딩되며
 * 실제 키는 {@code backend/.env} 의 {@code DART_API_KEY} 로 주입한다 (커밋 금지).
 *
 * <p>DART 는 OAuth 가 아니라 요청 쿼리 파라미터 {@code crtfc_key} 로 인증한다
 * (opendart.fss.or.kr 에서 발급, 40자리). base-url / *-path 는 2026-09 공개 스펙 기준.
 */
@ConfigurationProperties(prefix = "dart.api")
public record DartApiProperties(

        /** 발급받은 인증키. 미설정 시 호출 시점에 예외. */
        @DefaultValue("") String apiKey,

        @DefaultValue("https://opendart.fss.or.kr") String baseUrl,

        @DefaultValue("3s") Duration connectTimeout,

        /** corpCode.xml(ZIP) 다운로드/대용량 재무제표 JSON 대비 여유있게. */
        @DefaultValue("10s") Duration readTimeout,

        /** 기업개황. */
        @DefaultValue("/api/company.json") String companyPath,

        /** 단일회사 전체 재무제표. */
        @DefaultValue("/api/fnlttSinglAcntAll.json") String financialsPath,

        /** 고유번호(corp_code) 전체 목록 ZIP. */
        @DefaultValue("/api/corpCode.xml") String corpCodePath,

        /** 주식총수현황 (발행주식수 조회용). */
        @DefaultValue("/api/stockTotqySttus.json") String stockTotalCountPath
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
