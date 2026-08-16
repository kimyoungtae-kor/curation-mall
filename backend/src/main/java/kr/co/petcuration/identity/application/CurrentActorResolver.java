package kr.co.petcuration.identity.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorResolver {

    private final VisitorService visitorService;

    public CurrentActorResolver(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof PetUserPrincipal principal) {
            return Optional.of(principal.userId());
        }
        return Optional.empty();
    }

    public Optional<OwnerIdentity> findOwner(HttpServletRequest request) {
        Optional<UUID> userId = currentUserId();
        if (userId.isPresent()) {
            return userId.map(OwnerIdentity::member);
        }
        return visitorService.findExistingVisitorId(request).map(OwnerIdentity::visitor);
    }

    public OwnerIdentity requireOwner(HttpServletRequest request, HttpServletResponse response) {
        return currentUserId()
                .map(OwnerIdentity::member)
                .orElseGet(() -> OwnerIdentity.visitor(visitorService.requireVisitorId(request, response)));
    }
}
