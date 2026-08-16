package kr.co.petcuration.identity.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kr.co.petcuration.cart.application.GuestDataMergeService;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.identity.api.IdentityResponses.MergeResultResponse;
import kr.co.petcuration.identity.infrastructure.RoleEntity;
import kr.co.petcuration.identity.infrastructure.RoleRepository;
import kr.co.petcuration.identity.infrastructure.UserEntity;
import kr.co.petcuration.identity.infrastructure.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final GuestDataMergeService mergeService;
    private final Clock clock;

    public AuthenticationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            GuestDataMergeService mergeService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.mergeService = mergeService;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public AuthenticationResult signup(
            String email,
            String password,
            String name,
            String phone,
            UUID visitorId
    ) {
        validateBcryptLength(password);
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByNormalizedEmail(normalizedEmail)) {
            throw emailAlreadyExists();
        }
        RoleEntity customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is missing."));
        Instant now = clock.instant();
        UserEntity user = new UserEntity(
                UUID.randomUUID(), email.strip(), normalizedEmail, passwordEncoder.encode(password),
                name.strip(), normalizePhone(phone), customerRole, now
        );
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw emailAlreadyExists();
        }
        PetUserPrincipal principal = PetUserPrincipal.from(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()
        );
        MergeResultResponse mergeResult = mergeService.discardForSignup(user.getId(), visitorId);
        return new AuthenticationResult(authentication, principal, mergeResult);
    }

    @Transactional
    public AuthenticationResult login(String email, String password, UUID visitorId) {
        String normalizedEmail = normalizeEmail(email);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, password)
            );
        } catch (AuthenticationException exception) {
            throw invalidCredentials();
        }
        if (!(authentication.getPrincipal() instanceof PetUserPrincipal principal)) {
            throw invalidCredentials();
        }
        UserEntity user = userRepository.findByIdForUpdate(principal.userId()).orElseThrow(this::invalidCredentials);
        user.recordLogin(clock.instant());
        PetUserPrincipal refreshedPrincipal = PetUserPrincipal.from(user);
        MergeResultResponse mergeResult = refreshedPrincipal.roles().contains("ADMIN")
                ? new MergeResultResponse(false, 0, 0, List.of())
                : mergeService.merge(user.getId(), visitorId);
        return new AuthenticationResult(authentication, refreshedPrincipal, mergeResult);
    }

    public String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9+]", "");
    }

    private void validateBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "입력값을 확인해 주세요.",
                    "비밀번호가 너무 깁니다."
            );
        }
    }

    private ApiException emailAlreadyExists() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS",
                "이미 가입된 이메일입니다.",
                "다른 이메일을 사용하거나 로그인해 주세요."
        );
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "로그인 정보를 확인해 주세요.",
                "이메일 또는 비밀번호가 올바르지 않습니다."
        );
    }

    public record AuthenticationResult(
            Authentication authentication,
            PetUserPrincipal principal,
            MergeResultResponse mergeResult
    ) {
    }
}
