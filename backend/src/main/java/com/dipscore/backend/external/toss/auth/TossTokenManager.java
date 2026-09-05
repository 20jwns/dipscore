package com.dipscore.backend.external.toss.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import com.dipscore.backend.external.toss.TossApiException;
import com.dipscore.backend.external.toss.TossApiProperties;
import com.dipscore.backend.external.toss.auth.dto.TossTokenResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰을 메모리에 캐싱하고, 만료 임박(refresh-skew) 시 자동으로 갱신/재발급한다.
 * 여러 스레드가 동시에 만료된 토큰을 만나도 재발급은 1회만 일어난다.
 *
 * <p><b>주의</b>: 토스증권 오픈API 는 client 당 유효 토큰이 1개뿐이며, refresh_token 을 발급하지 않고
 * 재발급(POST /oauth2/token) 시 이전 토큰을 즉시 무효화한다. 따라서 백엔드를 여러 인스턴스로
 * 수평 확장하면 인스턴스끼리 서로의 토큰을 무효화시킬 수 있다 — 이 경우 이 클래스의 메모리 캐시를
 * Redis 등 공유 저장소로 옮겨야 한다. 단일 인스턴스 기동(현재 범위)에서는 문제 없음.
 */
@Component
public class TossTokenManager {

    private static final Logger log = LoggerFactory.getLogger(TossTokenManager.class);

    /** 응답에 expires_in 이 없을 때 사용할 보수적 TTL. */
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(5);

    private final TossOAuthClient oauthClient;
    private final Duration refreshSkew;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile Token current;

    @Autowired
    public TossTokenManager(TossOAuthClient oauthClient, TossApiProperties props) {
        this(oauthClient, props, Clock.systemUTC());
    }

    /** 커스텀 {@link Clock} 주입용 (테스트). */
    public TossTokenManager(TossOAuthClient oauthClient, TossApiProperties props, Clock clock) {
        this.oauthClient = oauthClient;
        this.refreshSkew = props.auth().refreshSkew();
        this.clock = clock;
    }

    /** 유효한 액세스 토큰을 반환한다. 필요 시 갱신/재발급. */
    public String getAccessToken() {
        Token t = current;
        if (t != null && t.isFresh(clock.instant(), refreshSkew)) {
            return t.accessToken();
        }
        lock.lock();
        try {
            t = current;
            if (t != null && t.isFresh(clock.instant(), refreshSkew)) {
                return t.accessToken();
            }
            Token obtained = obtain(t);
            current = obtained;
            return obtained.accessToken();
        } finally {
            lock.unlock();
        }
    }

    /** 캐시된 토큰을 버린다. 다음 호출 시 재발급 (예: API 가 401 반환). */
    public void invalidate() {
        current = null;
    }

    private Token obtain(Token previous) {
        if (previous != null && previous.refreshToken() != null && !previous.refreshToken().isBlank()) {
            try {
                return Token.from(oauthClient.refresh(previous.refreshToken()), clock.instant());
            } catch (TossApiException e) {
                log.warn("토큰 갱신 실패, 신규 발급으로 폴백: {}", e.getMessage());
            }
        }
        return Token.from(oauthClient.issue(), clock.instant());
    }

    private record Token(String accessToken, String refreshToken, Instant expiresAt) {

        static Token from(TossTokenResponse res, Instant now) {
            Duration ttl = res.expiresIn() != null && res.expiresIn() > 0
                    ? Duration.ofSeconds(res.expiresIn())
                    : FALLBACK_TTL;
            return new Token(res.accessToken(), res.refreshToken(), now.plus(ttl));
        }

        boolean isFresh(Instant now, Duration skew) {
            return now.isBefore(expiresAt.minus(skew));
        }
    }
}
