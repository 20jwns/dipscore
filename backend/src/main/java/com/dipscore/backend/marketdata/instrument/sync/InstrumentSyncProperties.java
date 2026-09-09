package com.dipscore.backend.marketdata.instrument.sync;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DART 고유번호 파일로 {@code instrument} 를 대량 채우는 배치 설정 ({@code instrument-sync.*}).
 * {@code scheduled=true} 면 {@link InstrumentSyncJob} 이 매일 {@code cron} 에 자동 실행하고,
 * 언제든 {@code POST /api/admin/instrument-sync} 로 수동 트리거할 수 있다.
 * (테스트 컨텍스트엔 이 키가 없어 관련 빈이 아예 생성되지 않는다.)
 */
@ConfigurationProperties(prefix = "instrument-sync")
public record InstrumentSyncProperties(

        @DefaultValue("false") boolean enabled,

        /** 매일 자동 실행 여부. false 면 스케줄러 잡만 미생성 (수동 엔드포인트는 유지). */
        @DefaultValue("true") boolean scheduled,

        /** 자동 실행 cron (Asia/Seoul). 15분+ 걸리므로 재무/거시 배치와 안 겹치는 새벽으로. */
        @DefaultValue("0 0 4 * * *") String cron,

        /** 한 트랜잭션에 upsert 할 행 수. 청크 하나가 실패해도 나머지는 계속 진행. */
        @DefaultValue("500") int chunkSize,

        /**
         * true 면 각 상장 종목을 DART 기업개황으로 조회해 {@code corp_cls} 를 확인하고
         * 코넥스({@code N}) 종목을 제외한다. 3천여 건 개별 호출 → 수십 분 소요.
         * 엔드포인트 {@code ?filterByCorpCls=false} 로 호출별 override 가능.
         */
        @DefaultValue("true") boolean filterByCorpCls,

        /**
         * true 면 전체 동기화 후, DART 목록에서 {@code staleDaysBeforeDeactivate} 일 이상 확인 안 된
         * 활성 KR_STOCK 을 {@code active=false} 로 전환한다 (상장폐지 감지).
         * onlySymbols 지정 실행이나 청크 실패가 있으면 이번 실행에서는 스윕을 건너뛴다.
         */
        @DefaultValue("true") boolean detectDelistings,

        /** 상장폐지로 판정하기까지의 유예일 (DART 일시 조회 실패 오탐 방지). */
        @DefaultValue("3") int staleDaysBeforeDeactivate,

        /** 기업개황 호출 사이 딜레이 (rate limit 완화). */
        @DefaultValue("100") long companyLookupDelayMs,

        /** 기업개황 호출이 이 횟수만큼 연속 실패하면 중단 (일일 한도 소진 등). 남은 종목은 필터 없이 포함. */
        @DefaultValue("20") int companyLookupMaxConsecutiveFailures
) {}
