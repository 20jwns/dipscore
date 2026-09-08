package com.dipscore.backend.web;

import com.dipscore.backend.marketdata.instrument.sync.InstrumentSyncProperties;
import com.dipscore.backend.marketdata.instrument.sync.InstrumentSyncService;
import java.util.List;

import com.dipscore.backend.marketdata.instrument.sync.InstrumentSyncService.InstrumentSyncReport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * DART 고유번호 파일로 {@code instrument} 대량 채우기 (일회성 관리자용). 자동 실행 안 함.
 * ({@code instrument-sync.enabled=true} 일 때만 등록)
 *
 * <p>{@code POST /api/admin/instrument-sync} — 설정({@code instrument-sync.filter-by-corp-cls}) 기본값 사용.
 * <br>{@code ?filterByCorpCls=false} — corp_cls 필터 끄고 빠르게(수 초).
 * <br>{@code ?symbols=005930,000660} — 지정 종목만 (소규모 검증·재동기화).
 */
@RestController
@ConditionalOnProperty(prefix = "instrument-sync", name = "enabled", havingValue = "true")
public class InstrumentSyncController {

    private final InstrumentSyncService service;
    private final InstrumentSyncProperties props;

    public InstrumentSyncController(InstrumentSyncService service, InstrumentSyncProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostMapping("/api/admin/instrument-sync")
    public InstrumentSyncReport sync(@RequestParam(required = false) Boolean filterByCorpCls,
                                     @RequestParam(required = false) List<String> symbols) {
        boolean filter = filterByCorpCls != null ? filterByCorpCls : props.filterByCorpCls();
        return service.sync(filter, symbols == null ? List.of() : symbols);
    }
}
