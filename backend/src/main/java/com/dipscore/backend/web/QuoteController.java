package com.dipscore.backend.web;

import com.dipscore.backend.external.toss.quote.TossQuoteClient;
import com.dipscore.backend.external.toss.quote.dto.TossPriceQuote;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 토스증권 시세 연동 스모크 테스트용 엔드포인트.
 * (정식 스코어/랭킹 API 로 대체될 임시 창구)
 */
@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final TossQuoteClient quoteClient;

    public QuoteController(TossQuoteClient quoteClient) {
        this.quoteClient = quoteClient;
    }

    @GetMapping("/{symbol}")
    public TossPriceQuote quote(@PathVariable String symbol) {
        return quoteClient.getQuote(symbol);
    }
}
