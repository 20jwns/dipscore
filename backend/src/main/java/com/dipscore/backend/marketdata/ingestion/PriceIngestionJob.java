package com.dipscore.backend.marketdata.ingestion;

import java.util.List;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.quote.TossQuoteClient;
import com.dipscore.backend.external.toss.quote.dto.TossPriceQuote;
import com.dipscore.backend.marketdata.price.PriceHistoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주기적으로 토스증권 시세를 조회해 {@code price_history} 하이퍼테이블에 적재한다.
 * 1차 목표는 스케줄링 파이프라인 동작 검증(기본 삼성전자 1종목).
 *
 * <p>{@code dipscore.ingestion.price.enabled=true} 일 때만 빈이 생성된다.
 * (테스트 컨텍스트에는 이 프로퍼티가 없어 스케줄러가 뜨지 않는다 → 외부 API 호출 방지)
 */
@Component
@ConditionalOnProperty(prefix = "dipscore.ingestion.price", name = "enabled", havingValue = "true")
public class PriceIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(PriceIngestionJob.class);
    private static final String SOURCE = "TOSS_QUOTE";

    private final TossQuoteClient tossQuoteClient;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceIngestionProperties props;

    public PriceIngestionJob(TossQuoteClient tossQuoteClient,
                             PriceHistoryRepository priceHistoryRepository,
                             PriceIngestionProperties props) {
        this.tossQuoteClient = tossQuoteClient;
        this.priceHistoryRepository = priceHistoryRepository;
        this.props = props;
    }

    /**
     * 한 주기 실행: 대상 종목 현재가를 일괄 조회해 upsert.
     * TODO: HTTP 조회를 트랜잭션 밖으로 분리 (현재는 뼈대 단순화를 위해 한 트랜잭션).
     */
    @Scheduled(
            fixedDelayString = "${dipscore.ingestion.price.interval-ms:60000}",
            initialDelayString = "${dipscore.ingestion.price.initial-delay-ms:10000}")
    @Transactional
    public void runOnce() {
        List<String> symbols = props.symbols();
        if (symbols == null || symbols.isEmpty()) {
            log.warn("[price-ingestion] 대상 종목이 없어 건너뜀");
            return;
        }

        try {
            List<TossPriceQuote> quotes = tossQuoteClient.getQuotes(symbols);
            int saved = 0;
            for (TossPriceQuote q : quotes) {
                if (q.timestamp() == null || q.lastPrice() == null) {
                    log.warn("[price-ingestion] 불완전 응답 건너뜀: {}", q);
                    continue;
                }
                priceHistoryRepository.upsertClose(q.symbol(), q.timestamp(), q.lastPrice(), SOURCE);
                saved++;
            }
            log.info("[price-ingestion] {}건 저장 (요청 {}종목: {})", saved, symbols.size(), symbols);
        } catch (TossApiException e) {
            log.warn("[price-ingestion] 토스 시세 조회 실패, 이번 주기 건너뜀: {}", e.getMessage());
        }
    }
}
