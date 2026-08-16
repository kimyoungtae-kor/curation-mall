package kr.co.petcuration.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.common.api.ApiErrorResponse;
import kr.co.petcuration.identity.config.VisitorCookieProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CorsProperties.class, VisitorCookieProperties.class})
public class SecurityConfig {

    private static final String TYPE_PREFIX = "https://pet-curation-mall.example/problems/";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper
    ) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/api/v1/payments/webhooks/**"))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityProblem(
                                objectMapper,
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "AUTHENTICATION_REQUIRED",
                                "로그인이 필요합니다.",
                                "회원 인증 후 다시 시도해 주세요."
                        ))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrfFailure = exception instanceof CsrfException;
                            writeSecurityProblem(
                                    objectMapper,
                                    request,
                                    response,
                                    HttpServletResponse.SC_FORBIDDEN,
                                    csrfFailure ? "CSRF_INVALID" : "ACCESS_DENIED",
                                    csrfFailure ? "보안 토큰을 확인해 주세요." : "접근 권한이 없습니다.",
                                    csrfFailure
                                            ? "CSRF 토큰을 다시 발급받은 뒤 요청해 주세요."
                                            : "이 작업을 수행할 권한이 없습니다."
                            );
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/products", "/api/v1/catalog/products/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/home", "/api/v1/collections", "/api/v1/collections/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/cart", "/api/v1/cart/**").permitAll()
                        .requestMatchers("/api/v1/wishlist", "/api/v1/wishlist/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/quote")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/guest-orders/lookup", "/api/v1/payments/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/media/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(VisitorCookieProperties cookieProperties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(builder -> builder
                .sameSite("Lax")
                .secure(cookieProperties.secure()));
        return repository;
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                "X-XSRF-TOKEN",
                "Idempotency-Key",
                "X-Request-Id"
        ));
        configuration.setExposedHeaders(List.of("Idempotent-Replayed", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void writeSecurityProblem(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String title,
            String detail
    ) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String traceId = request.getHeader("X-Request-Id") == null
                ? UUID.randomUUID().toString()
                : request.getHeader("X-Request-Id");
        ApiErrorResponse body = new ApiErrorResponse(
                TYPE_PREFIX + code.toLowerCase().replace('_', '-'),
                title,
                status,
                code,
                detail,
                request.getRequestURI(),
                traceId,
                Instant.now(),
                List.of()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
