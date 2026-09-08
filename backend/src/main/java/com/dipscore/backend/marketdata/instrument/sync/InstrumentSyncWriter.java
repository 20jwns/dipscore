package com.dipscore.backend.marketdata.instrument.sync;

import java.util.List;

import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.dipscore.backend.marketdata.instrument.InstrumentRepository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 청크(수백 건)를 {@code instrument} 에 upsert — 청크 단위 트랜잭션.
 * 청크 하나가 실패해도 앞서 커밋된 청크는 남는다.
 */
@Component
@ConditionalOnProperty(prefix = "instrument-sync", name = "enabled", havingValue = "true")
public class InstrumentSyncWriter {

    /** instrument.name 컬럼 길이. 초과 회사명은 잘라서 청크 전체 실패를 막는다. */
    private static final int NAME_MAX = 100;

    private final InstrumentRepository instrumentRepository;

    public InstrumentSyncWriter(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    /**
     * @param active null = active 안 건드림(필터 OFF), true = 활성, false = 비활성(코넥스 soft-delete)
     */
    @Transactional
    public void upsertChunk(List<DartCorpCode> chunk, Boolean active) {
        for (DartCorpCode c : chunk) {
            instrumentRepository.upsertFromDart(c.stockCode().trim(), truncate(c.corpName()), c.corpCode(), active);
        }
    }

    private static String truncate(String name) {
        String n = name == null ? "" : name.trim();
        return n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n;
    }
}
