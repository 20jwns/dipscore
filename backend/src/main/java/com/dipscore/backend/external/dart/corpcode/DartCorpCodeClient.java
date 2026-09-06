package com.dipscore.backend.external.dart.corpcode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.dipscore.backend.external.dart.DartApiException;
import com.dipscore.backend.external.dart.DartApiProperties;
import com.dipscore.backend.external.dart.corpcode.dto.DartCorpCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * DART 고유번호(corp_code) 전체 목록 조회 ({@code /api/corpCode.xml} → ZIP(CORPCODE.xml)).
 *
 * <p>키 오류 시 DART 는 ZIP 대신 {@code {"status":"010","message":"..."}} JSON 을 200 으로 준다.
 * <p>TODO: 목록은 자주 안 바뀌므로 실제로는 캐시/DB 적재 후 재사용해야 한다 (매 호출 ~수MB 다운로드+파싱).
 */
@Component
public class DartCorpCodeClient {

    private static final Logger log = LoggerFactory.getLogger(DartCorpCodeClient.class);

    private final RestClient dartRestClient;
    private final DartApiProperties props;
    private final ObjectMapper objectMapper;

    public DartCorpCodeClient(@Qualifier("dartRestClient") RestClient dartRestClient,
                              DartApiProperties props,
                              ObjectMapper objectMapper) {
        this.dartRestClient = dartRestClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /** 전체 고유번호 목록. */
    public List<DartCorpCode> downloadAll() {
        byte[] body;
        try {
            body = dartRestClient.get()
                    .uri(b -> b.path(props.corpCodePath()).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (rq, rp) -> {
                        throw new DartApiException("고유번호 파일 다운로드 실패: HTTP " + rp.getStatusCode().value());
                    })
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new DartApiException("고유번호 파일 다운로드 통신 오류", e);
        }

        if (body == null || body.length == 0) {
            throw new DartApiException("고유번호 파일 응답이 비어 있습니다.");
        }
        if (!isZip(body)) {
            throw new DartApiException("고유번호 파일 다운로드 실패: " + describeErrorJson(body));
        }
        List<DartCorpCode> list = parseXml(unzipFirstEntry(body));
        log.debug("DART 고유번호 {}건 로드", list.size());
        return list;
    }

    /** 상장 종목코드로 고유번호 1건 조회 (예: {@code "005930"} → 삼성전자 {@code 00126380}). */
    public Optional<DartCorpCode> findByStockCode(String stockCode) {
        String target = stockCode == null ? "" : stockCode.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("stockCode 는 필수입니다.");
        }
        return downloadAll().stream()
                .filter(c -> target.equals(c.stockCode()))
                .findFirst();
    }

    private static boolean isZip(byte[] b) {
        return b.length >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 0x03 && b[3] == 0x04;
    }

    private String describeErrorJson(byte[] body) {
        try {
            JsonNode n = objectMapper.readTree(body);
            return "status=%s, %s".formatted(n.path("status").asText(), n.path("message").asText());
        } catch (IOException e) {
            return "알 수 없는 응답(ZIP 아님)";
        }
    }

    private static byte[] unzipFirstEntry(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            if (entry == null) {
                throw new DartApiException("고유번호 ZIP 에 파일이 없습니다.");
            }
            return zis.readAllBytes();
        } catch (IOException e) {
            throw new DartApiException("고유번호 ZIP 해제 실패", e);
        }
    }

    private static List<DartCorpCode> parseXml(byte[] xmlBytes) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        List<DartCorpCode> out = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
            XMLStreamReader r = factory.createXMLStreamReader(in, "UTF-8");
            String current = null;
            StringBuilder corpCode = new StringBuilder();
            StringBuilder corpName = new StringBuilder();
            StringBuilder stockCode = new StringBuilder();
            StringBuilder modifyDate = new StringBuilder();

            while (r.hasNext()) {
                int event = r.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        current = r.getLocalName();
                        if ("list".equals(current)) {
                            corpCode.setLength(0);
                            corpName.setLength(0);
                            stockCode.setLength(0);
                            modifyDate.setLength(0);
                        }
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (current != null) {
                            switch (current) {
                                case "corp_code" -> corpCode.append(r.getText());
                                case "corp_name" -> corpName.append(r.getText());
                                case "stock_code" -> stockCode.append(r.getText());
                                case "modify_date" -> modifyDate.append(r.getText());
                                default -> { }
                            }
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if ("list".equals(r.getLocalName())) {
                            out.add(new DartCorpCode(
                                    trimToNull(corpCode), trimToNull(corpName),
                                    trimToNull(stockCode), trimToNull(modifyDate)));
                        }
                        current = null;
                    }
                    default -> { }
                }
            }
            r.close();
        } catch (XMLStreamException | IOException e) {
            throw new DartApiException("고유번호 XML 파싱 실패", e);
        }
        return out;
    }

    private static String trimToNull(CharSequence cs) {
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
