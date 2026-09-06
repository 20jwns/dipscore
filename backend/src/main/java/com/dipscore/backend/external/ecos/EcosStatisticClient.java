package com.dipscore.backend.external.ecos;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import com.dipscore.backend.external.ecos.EcosApiProperties.Indicator;
import com.dipscore.backend.external.ecos.dto.EcosStatisticResponse;
import com.dipscore.backend.external.ecos.dto.EcosStatisticRow;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 한국은행 ECOS {@code StatisticSearch} 조회. 기준금리 / 원·달러 환율 / CPI 등
 * 설정({@link EcosApiProperties.Indicators})에 정의된 지표의 최근값을 가져온다.
 */
@Component
public class EcosStatisticClient {

    private static final Logger log = LoggerFactory.getLogger(EcosStatisticClient.class);
    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    private final RestClient ecosRestClient;
    private final EcosApiProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public EcosStatisticClient(@Qualifier("ecosRestClient") RestClient ecosRestClient,
                               EcosApiProperties props,
                               ObjectMapper objectMapper) {
        this(ecosRestClient, props, objectMapper, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    /** 커스텀 {@link Clock} 주입용 (테스트). */
    public EcosStatisticClient(RestClient ecosRestClient, EcosApiProperties props,
                               ObjectMapper objectMapper, Clock clock) {
        this.ecosRestClient = ecosRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 한국은행 기준금리 최근값 (월). */
    public EcosStatisticRow latestBaseRate() {
        return latest(props.indicators().baseRate(), 6);
    }

    /** 원/달러 매매기준율 최근값 (일). 주말·공휴일 고려해 조금 넉넉히 조회. */
    public EcosStatisticRow latestExchangeRate() {
        return latest(props.indicators().exchangeRate(), 10);
    }

    /** 소비자물가지수(총지수) 최근값 (월). */
    public EcosStatisticRow latestCpi() {
        return latest(props.indicators().cpi(), 6);
    }

    /**
     * @param lookbackUnits 조회 창 크기. 결과 행 수가 {@code ecos.api.max-rows} 를 넘지 않도록 작게 잡는다
     *                      (창 안 데이터를 모두 받아 그중 TIME 최대값을 고름).
     */
    private EcosStatisticRow latest(Indicator ind, int lookbackUnits) {
        LocalDate today = LocalDate.now(clock);
        String start;
        String end;
        if ("D".equalsIgnoreCase(ind.cycle())) {
            end = today.format(YMD);
            start = today.minusDays(lookbackUnits).format(YMD);
        } else {
            YearMonth ym = YearMonth.from(today);
            end = ym.format(YM);
            start = ym.minusMonths(lookbackUnits).format(YM);
        }
        return search(ind.statCode(), ind.cycle(), start, end, ind.itemCode()).stream()
                .max(Comparator.comparing(EcosStatisticRow::time))
                .orElseThrow(() -> new EcosApiException(
                        "ECOS 응답에 데이터가 없습니다: " + ind.statCode() + "/" + ind.itemCode()));
    }

    /**
     * 통계 조회. 시점 형식은 주기에 맞춰야 한다(일 {@code YYYYMMDD}, 월 {@code YYYYMM}).
     */
    public List<EcosStatisticRow> search(String statCode, String cycle,
                                         String startTime, String endTime, String itemCode) {
        if (!props.hasApiKey()) {
            throw new EcosApiException("ECOS API 키가 없습니다. backend/.env 에 ECOS_API_KEY 를 설정하세요.");
        }
        if (!StringUtils.hasText(statCode) || !StringUtils.hasText(cycle)
                || !StringUtils.hasText(startTime) || !StringUtils.hasText(endTime)
                || !StringUtils.hasText(itemCode)) {
            throw new IllegalArgumentException("statCode/cycle/startTime/endTime/itemCode 는 모두 필수입니다.");
        }

        String bodyText;
        try {
            bodyText = ecosRestClient.get()
                    .uri(b -> b.path(props.statisticSearchPath())
                            .pathSegment(props.apiKey(), "json", props.language(),
                                    "1", String.valueOf(props.maxRows()),
                                    statCode, cycle, startTime, endTime, itemCode)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (rq, rp) -> {
                        throw new EcosApiException("ECOS 조회 실패(%s): HTTP %d"
                                .formatted(statCode, rp.getStatusCode().value()));
                    })
                    // ECOS 가 text/html content-type 으로 JSON 을 주는 경우가 있어 문자열로 받아 직접 파싱
                    .body(String.class);
        } catch (RestClientException e) {
            throw new EcosApiException("ECOS 조회 통신 오류: " + statCode, e);
        }

        if (!StringUtils.hasText(bodyText)) {
            throw new EcosApiException("ECOS 응답이 비어 있습니다: " + statCode);
        }

        EcosStatisticResponse resp;
        try {
            resp = objectMapper.readValue(bodyText, EcosStatisticResponse.class);
        } catch (Exception e) {
            throw new EcosApiException("ECOS 응답 파싱 실패: " + statCode, e);
        }

        if (resp.result() != null) {
            throw new EcosApiException("ECOS 조회 실패 (code=%s): %s"
                    .formatted(resp.result().code(), resp.result().message()));
        }
        if (resp.statisticSearch() == null || resp.statisticSearch().row() == null) {
            throw new EcosApiException("ECOS 응답 형식 오류(StatisticSearch 없음): " + statCode);
        }
        List<EcosStatisticRow> rows = resp.statisticSearch().row();
        log.debug("ECOS {} {}건 조회 ({}~{})", statCode, rows.size(), startTime, endTime);
        return rows;
    }
}
