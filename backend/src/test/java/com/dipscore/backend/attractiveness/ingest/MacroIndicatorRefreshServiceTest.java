package com.dipscore.backend.attractiveness.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService.MacroRefreshResult;
import com.dipscore.backend.attractiveness.ingest.MacroIndicatorRefreshService.RefreshStatus;
import com.dipscore.backend.external.ecos.EcosApiException;
import com.dipscore.backend.external.ecos.EcosApiProperties;
import com.dipscore.backend.external.ecos.EcosApiProperties.Indicator;
import com.dipscore.backend.external.ecos.EcosApiProperties.Indicators;
import com.dipscore.backend.external.ecos.EcosStatisticClient;
import com.dipscore.backend.external.ecos.dto.EcosStatisticRow;
import com.dipscore.backend.marketdata.macro.MacroIndicator;
import com.dipscore.backend.marketdata.macro.MacroIndicatorId;
import com.dipscore.backend.marketdata.macro.MacroIndicatorRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MacroIndicatorRefreshServiceTest {

    @Mock EcosStatisticClient ecos;
    @Mock MacroIndicatorRepository repository;

    private static final EcosApiProperties ECOS_PROPS = new EcosApiProperties(
            "key", "https://ecos.example", Duration.ofSeconds(3), Duration.ofSeconds(10),
            "kr", "/api/StatisticSearch", 100,
            new Indicators(
                    new Indicator("722Y001", "0101000", "M"),
                    new Indicator("731Y001", "0000001", "D"),
                    new Indicator("901Y009", "0", "M")));

    private MacroIndicatorRefreshService service() {
        return new MacroIndicatorRefreshService(ecos, ECOS_PROPS, repository);
    }

    private static EcosStatisticRow row(String time, String value) {
        return new EcosStatisticRow(null, null, null, null, null, time, value);
    }

    @Test
    void 환율_최신값의_시점이_없으면_insert한다() {
        when(ecos.latestExchangeRate()).thenReturn(row("20260908", "1376.5"));
        when(repository.findById(any())).thenReturn(Optional.empty());

        MacroRefreshResult r = service().refreshLatest("USD_KRW");

        Instant ts = Instant.parse("2026-09-08T00:00:00Z");
        verify(repository).upsert("USD_KRW", ts, new BigDecimal("1376.5"), "KRW", "ECOS_731Y001");
        assertThat(r.status()).isEqualTo(RefreshStatus.INSERTED);
        assertThat(r.ts()).isEqualTo(ts);
        assertThat(r.value()).isEqualByComparingTo("1376.5");
    }

    @Test
    void 이미_존재하는_시점은_skip하고_upsert하지_않는다() {
        when(ecos.latestExchangeRate()).thenReturn(row("20260908", "1376.5"));
        Instant ts = Instant.parse("2026-09-08T00:00:00Z");
        when(repository.findById(new MacroIndicatorId("USD_KRW", ts)))
                .thenReturn(Optional.of(mock(MacroIndicator.class)));

        MacroRefreshResult r = service().refreshLatest("USD_KRW");

        verify(repository, never()).upsert(any(), any(), any(), any(), any());
        assertThat(r.status()).isEqualTo(RefreshStatus.SKIPPED_EXISTS);
    }

    @Test
    void 기준금리_월지표는_시점을_월초_UTC로_변환해_적재한다() {
        when(ecos.latestBaseRate()).thenReturn(row("202609", "2.75"));
        when(repository.findById(any())).thenReturn(Optional.empty());

        service().refreshLatest("BASE_RATE");

        verify(repository).upsert(eq("BASE_RATE"), eq(Instant.parse("2026-09-01T00:00:00Z")),
                eq(new BigDecimal("2.75")), eq("%"), eq("ECOS_722Y001"));
    }

    @Test
    void CPI_최신값을_지수단위로_적재한다() {
        when(ecos.latestCpi()).thenReturn(row("202608", "116.35"));
        when(repository.findById(any())).thenReturn(Optional.empty());

        service().refreshLatest("CPI");

        verify(repository).upsert(eq("CPI"), eq(Instant.parse("2026-08-01T00:00:00Z")),
                eq(new BigDecimal("116.35")), eq("2020=100"), eq("ECOS_901Y009"));
    }

    @Test
    void ECOS_최신값이_비어있으면_예외() {
        when(ecos.latestExchangeRate()).thenReturn(row("20260908", ""));

        assertThatThrownBy(() -> service().refreshLatest("USD_KRW"))
                .isInstanceOf(EcosApiException.class)
                .hasMessageContaining("비어");
    }

    @Test
    void 지원하지_않는_지표코드는_예외() {
        assertThatThrownBy(() -> service().refreshLatest("GDP"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
