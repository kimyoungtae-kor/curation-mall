package kr.co.petcuration.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import java.util.UUID;
import kr.co.petcuration.cart.application.CartService;
import kr.co.petcuration.cart.application.GuestDataMergeService;
import kr.co.petcuration.cart.application.WishlistService;
import kr.co.petcuration.common.api.ApiException;
import kr.co.petcuration.identity.api.IdentityResponses.AuthenticationEnvelope;
import kr.co.petcuration.identity.api.IdentityResponses.AuthenticationResponse;
import kr.co.petcuration.identity.api.IdentityResponses.CsrfEnvelope;
import kr.co.petcuration.identity.api.IdentityResponses.CsrfResponse;
import kr.co.petcuration.identity.api.IdentityResponses.MeEnvelope;
import kr.co.petcuration.identity.api.IdentityResponses.MeResponse;
import kr.co.petcuration.identity.api.IdentityResponses.MergeResultEnvelope;
import kr.co.petcuration.identity.api.IdentityResponses.MergeResultResponse;
import kr.co.petcuration.identity.api.IdentityResponses.UserResponse;
import kr.co.petcuration.identity.application.AuthenticationService;
import kr.co.petcuration.identity.application.AuthenticationService.AuthenticationResult;
import kr.co.petcuration.identity.application.CurrentActorResolver;
import kr.co.petcuration.identity.application.OwnerIdentity;
import kr.co.petcuration.identity.application.PetUserPrincipal;
import kr.co.petcuration.identity.application.VisitorService;
import kr.co.petcuration.identity.config.VisitorCookieProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class IdentityController {

    private final AuthenticationService authenticationService;
    private final CurrentActorResolver actorResolver;
    private final VisitorService visitorService;
    private final CartService cartService;
    private final WishlistService wishlistService;
    private final GuestDataMergeService mergeService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final VisitorCookieProperties cookieProperties;

    public IdentityController(
            AuthenticationService authenticationService,
            CurrentActorResolver actorResolver,
            VisitorService visitorService,
            CartService cartService,
            WishlistService wishlistService,
            GuestDataMergeService mergeService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            VisitorCookieProperties cookieProperties
    ) {
        this.authenticationService = authenticationService;
        this.actorResolver = actorResolver;
        this.visitorService = visitorService;
        this.cartService = cartService;
        this.wishlistService = wishlistService;
        this.mergeService = mergeService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.cookieProperties = cookieProperties;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfEnvelope> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfEnvelope(new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken())));
    }

    @GetMapping("/me")
    ResponseEntity<MeEnvelope> me(HttpServletRequest request) {
        PetUserPrincipal principal = currentPrincipal().orElse(null);
        Optional<OwnerIdentity> owner = actorResolver.findOwner(request);
        int cartCount = owner.map(cartService::countItems).orElse(0);
        long wishlistCount = principal != null && principal.roles().contains("CUSTOMER")
                ? wishlistService.count(OwnerIdentity.member(principal.userId()))
                : 0L;
        MeResponse response = new MeResponse(
                principal != null,
                principal == null ? null : userResponse(principal),
                cartCount,
                wishlistCount
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MeEnvelope(response));
    }

    @PostMapping("/signup")
    ResponseEntity<AuthenticationEnvelope> signup(
            @Valid @RequestBody SignupRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UUID visitorId = visitorService.findExistingVisitorId(request).orElse(null);
        AuthenticationResult result = authenticationService.signup(
                body.email(), body.password(), body.name(), body.phone(), visitorId
        );
        establishSession(result, request, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(authenticationEnvelope(result));
    }

    @PostMapping("/login")
    ResponseEntity<AuthenticationEnvelope> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        UUID visitorId = visitorService.findExistingVisitorId(request).orElse(null);
        AuthenticationResult result = authenticationService.login(body.email(), body.password(), visitorId);
        establishSession(result, request, response);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authenticationEnvelope(result));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
        ResponseCookie expiredSession = ResponseCookie.from("SESSION", "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredSession.toString());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/merge-guest-data")
    ResponseEntity<MergeResultEnvelope> mergeGuestData(HttpServletRequest request) {
        PetUserPrincipal principal = currentPrincipal().orElseThrow();
        if (principal.roles().contains("ADMIN")) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_GUEST_MERGE_FORBIDDEN",
                    "관리자 계정에는 방문자 데이터를 합칠 수 없습니다.",
                    "고객 계정으로 로그인한 뒤 장바구니와 찜을 합쳐 주세요."
            );
        }
        UUID userId = principal.userId();
        UUID visitorId = visitorService.findExistingVisitorId(request).orElse(null);
        MergeResultResponse result = mergeService.merge(userId, visitorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new MergeResultEnvelope(result));
    }

    private void establishSession(
            AuthenticationResult result,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (request.getSession(false) == null) {
            request.getSession(true);
        } else {
            request.changeSessionId();
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                result.principal(), null, result.principal().getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);
    }

    private AuthenticationEnvelope authenticationEnvelope(AuthenticationResult result) {
        return new AuthenticationEnvelope(new AuthenticationResponse(
                true,
                userResponse(result.principal()),
                result.mergeResult()
        ));
    }

    private Optional<PetUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof PetUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    private UserResponse userResponse(PetUserPrincipal principal) {
        return new UserResponse(
                principal.userId(), principal.email(), principal.name(), principal.phone(), principal.roles()
        );
    }

    record SignupRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30)
            @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "휴대전화 번호가 올바르지 않습니다.")
            String phone,
            @AssertTrue(message = "필수 약관에 동의해 주세요.") boolean requiredTermsAccepted
    ) {
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 200) String password
    ) {
    }
}
