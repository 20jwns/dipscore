package com.dipscore.backend.web;

import java.util.List;

import com.dipscore.backend.marketdata.backfill.PriceBackfillProperties;
import com.dipscore.backend.marketdata.backfill.PriceBackfillService;
import com.dipscore.backend.marketdata.backfill.PriceBackfillService.PriceBackfillReport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 과거 시세 백필 수동 트리거. 앱 기동 시 자동 실행되지 않는다.
 * ({@code backfill.prices.enabled=true} 일 때만 등록)
 *
 * <p>예: {@code POST /api/admin/price-backfill}  (설정 기본값: 3종목, 365일)
 * <br> {@code POST /api/admin/price-backfill?symbols=005930,000660&days=90}
 */
@RestController
@ConditionalOnProperty(prefix = "backfill.prices", name = "enabled", havingValue = "true")
public class PriceBackfillController {

    private final PriceBackfillService service;
    private final PriceBackfillProperties props;

    public PriceBackfillController(PriceBackfillService service, PriceBackfillProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/api/admin/price-backfill")
    public PriceBackfillReport backfill(@RequestParam(required = false) List<String> symbols,
                                        @RequestParam(required = false) Integer days) {
        return service.backfill(
                symbols == null || symbols.isEmpty() ? props.symbols() : symbols,
                days == null ? props.days() : days);
    }
}
