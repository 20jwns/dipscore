package com.dipscore.backend.marketdata.instrument.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DART 고유번호 파일로 {@code instrument} 를 대량 채우는 배치 설정 ({@code instrument-sync.*}).
 * {@code enabled=true} 여도 자동 실행은 없다 — {@code POST /api/admin/instrument-sync} 로만 수동 트리거.
 * (테스트 컨텍스트엔 이 키가 없어 관련 빈이 아예 생성되지 않는다.)
 */
@ConfigurationProperties(prefix = "instrument-sync")
public record InstrumentSyncProperties(

        @DefaultValue("false") boolean enabled,

        /** 한 트랜잭션에 upsert 할 행 수. 청크 하나가 실패해도 나머지는 계속 진행. */
        @DefaultValue("500") int chunkSize,

        /**
         * true 면 각 상장 종목을 DART 기업개황으로 조회해 {@code corp_cls} 를 확인하고
         * 코넥스({@code N}) 종목을 제외한다. 3천여 건 개별 호출 → 수십 분 소요.
         * 엔드포인트 {@code ?filterByCorpCls=false} 로 호출별 override 가능.
         */
        @DefaultValue("true") boolean filterByCorpCls,

        /** 기업개황 호출 사이 딜레이 (rate limit 완화). */
        @DefaultValue("100") long companyLookupDelayMs,

        /** 기업개황 호출이 이 횟수만큼 연속 실패하면 중단 (일일 한도 소진 등). 남은 종목은 필터 없이 포함. */
        @DefaultValue("20") int companyLookupMaxConsecutiveFailures
) {}
