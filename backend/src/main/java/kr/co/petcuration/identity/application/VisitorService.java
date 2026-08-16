package kr.co.petcuration.identity.application;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.identity.config.VisitorCookieProperties;
import kr.co.petcuration.identity.infrastructure.VisitorEntity;
import kr.co.petcuration.identity.infrastructure.VisitorRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.WebUtils;

@Service
public class VisitorService {

    public static final String COOKIE_NAME = "PET_VISITOR";
    private static final int TOKEN_BYTES = 32;

    private final VisitorRepository visitorRepository;
    private final VisitorCookieProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Autowired
    public VisitorService(VisitorRepository visitorRepository, VisitorCookieProperties properties) {
        this(visitorRepository, properties, Clock.systemUTC());
    }

    VisitorService(VisitorRepository visitorRepository, VisitorCookieProperties properties, Clock clock) {
        this.visitorRepository = visitorRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Optional<UUID> findExistingVisitorId(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie == null || cookie.getValue().length() < 20 || cookie.getValue().length() > 200) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return visitorRepository.findByTokenHash(hash(cookie.getValue()))
                .filter(visitor -> !visitor.isExpired(now))
                .map(visitor -> {
                    visitor.touch(now, now.plus(properties.maxAge()));
                    return visitor.getId();
                });
    }

    @Transactional
    public UUID requireVisitorId(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        Instant now = clock.instant();
        if (cookie != null && cookie.getValue().length() >= 20 && cookie.getValue().length() <= 200) {
            Optional<VisitorEntity> existing = visitorRepository.findByTokenHash(hash(cookie.getValue()));
            if (existing.isPresent() && !existing.get().isExpired(now)) {
                existing.get().touch(now, now.plus(properties.maxAge()));
                writeCookie(response, cookie.getValue());
                return existing.get().getId();
            }
        }

        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        VisitorEntity visitor = new VisitorEntity(
                UUID.randomUUID(),
                hash(rawToken),
                now.plus(properties.maxAge()),
                now
        );
        visitorRepository.save(visitor);
        writeCookie(response, rawToken);
        return visitor.getId();
    }

    private void writeCookie(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.maxAge())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }
}
