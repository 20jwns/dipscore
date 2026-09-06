package com.dipscore.backend.web;

import com.dipscore.backend.external.ecos.EcosStatisticClient;
import com.dipscore.backend.external.ecos.dto.EcosStatisticRow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 한국은행 ECOS 거시지표 스모크 테스트용 엔드포인트. (정식 매력도지수 거시 입력으로 대체될 임시 창구)
 */
@RestController
@RequestMapping("/api/ecos")
public class EcosController {

    private final EcosStatisticClient client;

    public EcosController(EcosStatisticClient client) {
        this.client = client;
    }

    /** 한국은행 기준금리 최근값. */
    @GetMapping("/base-rate")
    public EcosStatisticRow baseRate() {
        return client.latestBaseRate();
    }

    /** 원/달러 매매기준율 최근값. */
    @GetMapping("/exchange-rate")
    public EcosStatisticRow exchangeRate() {
        return client.latestExchangeRate();
    }

    /** 소비자물가지수(총지수) 최근값. */
    @GetMapping("/cpi")
    public EcosStatisticRow cpi() {
        return client.latestCpi();
    }
}
