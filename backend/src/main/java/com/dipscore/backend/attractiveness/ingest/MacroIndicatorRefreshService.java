package com.dipscore.backend.attractiveness.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.dipscore.backend.external.ecos.EcosApiProperties;
import com.dipscore.backend.external.ecos.EcosApiProperties.Indicator;
import com.dipscore.backend.external.ecos.EcosStatisticClient;
import com.dipscore.backend.external.ecos.dto.EcosStatisticRow;
import com.dipscore.backend.marketdata.macro.MacroIndicatorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ECOS → {@code macro_indicator} 적재 (스켈레톤). 최근 창을 조회해 upsert.
 * 실제 운영 시 스케줄러로 주기 실행 예정 (현재 미연결).
 */
@Service
@ConditionalOnProperty(prefix = "attractiveness", name = "enabled", havingValue = "true")
public class MacroIndicatorRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MacroIndicatorRefreshService.class);
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final EcosStatisticClient ecos;
    private final EcosApiProperties ecosProps;
    private final MacroIndicatorRepository repository;

    public MacroIndicatorRefreshService(EcosStatisticClient ecos,
                                        EcosApiProperties ecosProps,
                                        MacroIndicatorRepository repository) {
        this.ecos = ecos;
        this.ecosProps = ecosProps;
        this.repository = repository;
    }

    /**
     * @param indicatorCode {@code BASE_RATE} 또는 {@code USD_KRW}
     * @return 적재/갱신한 행 수
     */
    @Transactional
    public int refresh(String indicatorCode) {
        Indicator ind = switch (indicatorCode) {
            case "BASE_RATE" -> ecosProps.indicators().baseRate();
            case "USD_KRW" -> ecosProps.indicators().exchangeRate();
            default -> throw new IllegalArgumentException("지원하지 않는 지표: " + indicatorCode);
        };
        boolean daily = "D".equalsIgnoreCase(ind.cycle());
        DateTimeFormatter fmt = daily ? YMD : YM;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String start = (daily ? today.minusDays(90) : today.minusMonths(36)).format(fmt);
        String end = today.format(fmt);

        List<EcosStatisticRow> rows = ecos.search(ind.statCode(), ind.cycle(), start, end, ind.itemCode());
        String unit = daily ? "KRW" : "%";
        int n = 0;
        for (EcosStatisticRow row : rows) {
            BigDecimal value = row.dataValueAsDecimal();
            if (value == null || row.time() == null) {
                continue;
            }
            repository.upsert(indicatorCode, toInstant(row.time(), daily), value, unit, "ECOS_" + ind.statCode());
            n++;
        }
        log.info("[macro-refresh] {} {}건 적재 ({}~{})", indicatorCode, n, start, end);
        return n;
    }

    private static Instant toInstant(String time, boolean daily) {
        if (daily) {
            return LocalDate.parse(time, YMD).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return YearMonth.parse(time, YM).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
