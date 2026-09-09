package com.dipscore.backend.attractiveness.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.dipscore.backend.external.ecos.EcosApiException;
import com.dipscore.backend.external.ecos.EcosApiProperties;
import com.dipscore.backend.external.ecos.EcosApiProperties.Indicator;
import com.dipscore.backend.external.ecos.EcosStatisticClient;
import com.dipscore.backend.external.ecos.dto.EcosStatisticRow;
import com.dipscore.backend.marketdata.macro.MacroIndicatorId;
import com.dipscore.backend.marketdata.macro.MacroIndicatorRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ECOS → {@code macro_indicator} 적재. 검증된 {@link EcosStatisticClient} 의
 * {@code latestBaseRate()/latestExchangeRate()/latestCpi()} 를 재사용해 최신 관측치 1건을 가져오고,
 * 그 시점({@code ts})이 아직 없으면 insert / 있으면 skip 한다.
 *
 * <p>스케줄 실행은 {@link MacroIndicatorRefreshJob}, 수동 트리거는 {@code POST /api/admin/macro-sync}.
 * {@code attractiveness.enabled=true} 일 때만 빈 생성.
 */
@Service
@ConditionalOnProperty(prefix = "attractiveness", name = "enabled", havingValue = "true")
public class MacroIndicatorRefreshService {

    private static final Logger log = LoggerFactory.getLogger(MacroIndicatorRefreshService.class);
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    public static final String BASE_RATE = "BASE_RATE";
    public static final String USD_KRW = "USD_KRW";
    public static final String CPI = "CPI";
    /** 스케줄러·엔드포인트 기본 대상 (환율은 매일, 기준금리·CPI 는 월 1회). */
    public static final List<String> ALL_CODES = List.of(BASE_RATE, USD_KRW, CPI);

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
     * 지정 지표의 ECOS 최신값 1건을 조회 → 해당 시점이 없으면 insert, 있으면 skip.
     *
     * @param indicatorCode {@link #BASE_RATE} / {@link #USD_KRW} / {@link #CPI}
     */
    @Transactional
    public MacroRefreshResult refreshLatest(String indicatorCode) {
        Indicator ind = indicatorFor(indicatorCode);
        EcosStatisticRow row = switch (indicatorCode) {
            case BASE_RATE -> ecos.latestBaseRate();
            case USD_KRW -> ecos.latestExchangeRate();
            case CPI -> ecos.latestCpi();
            default -> throw new IllegalArgumentException("지원하지 않는 지표: " + indicatorCode);
        };

        BigDecimal value = row.dataValueAsDecimal();
        if (value == null || row.time() == null) {
            throw new EcosApiException("ECOS 최신값이 비어 있음: " + indicatorCode);
        }

        boolean daily = "D".equalsIgnoreCase(ind.cycle());
        Instant ts = toInstant(row.time(), daily);

        if (repository.findById(new MacroIndicatorId(indicatorCode, ts)).isPresent()) {
            log.info("[macro-refresh] {} {} 이미 존재, skip", indicatorCode, row.time());
            return new MacroRefreshResult(indicatorCode, ts, value, RefreshStatus.SKIPPED_EXISTS, null);
        }

        repository.upsert(indicatorCode, ts, value, unitFor(indicatorCode), "ECOS_" + ind.statCode());
        log.info("[macro-refresh] {} {} = {} 적재", indicatorCode, row.time(), value);
        return new MacroRefreshResult(indicatorCode, ts, value, RefreshStatus.INSERTED, null);
    }

    /**
     * 최근 창(일 90일 / 월 36개월)을 조회해 여러 시점을 한꺼번에 upsert (Z-score 분포 창 채우기용).
     * 스케줄러는 {@link #refreshLatest} 를 쓰고, 이 메서드는 히스토리 백필 시 수동 사용.
     *
     * @return upsert 한 행 수
     */
    @Transactional
    public int refreshWindow(String indicatorCode) {
        Indicator ind = indicatorFor(indicatorCode);
        boolean daily = "D".equalsIgnoreCase(ind.cycle());
        DateTimeFormatter fmt = daily ? YMD : YM;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String start = (daily ? today.minusDays(90) : today.minusMonths(36)).format(fmt);
        String end = today.format(fmt);

        List<EcosStatisticRow> rows = ecos.search(ind.statCode(), ind.cycle(), start, end, ind.itemCode());
        int n = 0;
        for (EcosStatisticRow row : rows) {
            BigDecimal value = row.dataValueAsDecimal();
            if (value == null || row.time() == null) {
                continue;
            }
            repository.upsert(indicatorCode, toInstant(row.time(), daily), value,
                    unitFor(indicatorCode), "ECOS_" + ind.statCode());
            n++;
        }
        log.info("[macro-refresh] {} 창 {}건 적재 ({}~{})", indicatorCode, n, start, end);
        return n;
    }

    private Indicator indicatorFor(String indicatorCode) {
        return switch (indicatorCode) {
            case BASE_RATE -> ecosProps.indicators().baseRate();
            case USD_KRW -> ecosProps.indicators().exchangeRate();
            case CPI -> ecosProps.indicators().cpi();
            default -> throw new IllegalArgumentException("지원하지 않는 지표: " + indicatorCode);
        };
    }

    private static String unitFor(String indicatorCode) {
        return switch (indicatorCode) {
            case BASE_RATE -> "%";
            case USD_KRW -> "KRW";
            case CPI -> "2020=100";
            default -> null;
        };
    }

    private static Instant toInstant(String time, boolean daily) {
        if (daily) {
            return LocalDate.parse(time, YMD).atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        return YearMonth.parse(time, YM).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public enum RefreshStatus {
        /** 새 시점 적재. */
        INSERTED,
        /** 해당 시점이 이미 있어 건너뜀. */
        SKIPPED_EXISTS,
        /** ECOS 조회/적재 실패 (job 이 채움). */
        FAILED
    }

    /**
     * @param ts     관측 시점 (월지표는 해당 월 1일 UTC). 실패면 null.
     * @param value  관측값. 실패면 null.
     * @param error  실패 사유 (성공/ skip 이면 null).
     */
    public record MacroRefreshResult(String indicatorCode, Instant ts, BigDecimal value,
                                     RefreshStatus status, String error) {}
}
