package kr.co.petcuration.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.visitor-cookie")
public record VisitorCookieProperties(boolean secure, Duration maxAge) {

    public VisitorCookieProperties {
        maxAge = maxAge == null ? Duration.ofDays(30) : maxAge;
        if (maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Visitor cookie max age must be positive.");
        }
    }
}
