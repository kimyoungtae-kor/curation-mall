package kr.co.petcuration.order.application;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.identity.application.PetUserPrincipal;

@Component
public class CommerceActorResolver {

    private static final String VISITOR_COOKIE = "PET_VISITOR";
    private final JdbcTemplate jdbcTemplate;

    public CommerceActorResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommerceActor resolve(HttpServletRequest request) {
        return resolveOptional(request).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "인증이 필요합니다.",
                        "로그인하거나 방문자 세션을 다시 발급받아 주세요."));
    }

    public Optional<CommerceActor> resolveOptional(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            if (authentication.getPrincipal() instanceof PetUserPrincipal principal) {
                return Optional.of(CommerceActor.member(principal.userId(), principal.roles().contains("ADMIN")));
            }
            String name = authentication.getName();
            List<UUID> ids = jdbcTemplate.query(
                    "SELECT id FROM users WHERE normalized_email = lower(?) OR CAST(id AS varchar) = ?",
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    name,
                    name
            );
            if (!ids.isEmpty()) {
                UUID userId = ids.getFirst();
                Boolean admin = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                             WHERE ur.user_id = ? AND r.code = 'ADMIN'
                        )
                        """, Boolean.class, userId);
                return Optional.of(CommerceActor.member(userId, Boolean.TRUE.equals(admin)));
            }
        }

        String token = findCookie(request, VISITOR_COOKIE);
        if (token != null) {
            List<UUID> ids = jdbcTemplate.query(
                    "SELECT id FROM visitors WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP",
                    (rs, rowNum) -> rs.getObject("id", UUID.class),
                    sha256(token)
            );
            if (!ids.isEmpty()) {
                return Optional.of(CommerceActor.visitor(ids.getFirst()));
            }
        }
        return Optional.empty();
    }

    private String findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
