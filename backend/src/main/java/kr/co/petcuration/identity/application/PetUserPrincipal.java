package kr.co.petcuration.identity.application;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.co.petcuration.identity.domain.UserStatus;
import kr.co.petcuration.identity.infrastructure.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class PetUserPrincipal implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final String phone;
    private final UserStatus status;
    private final List<String> roles;

    private PetUserPrincipal(
            UUID userId,
            String email,
            String passwordHash,
            String name,
            String phone,
            UserStatus status,
            List<String> roles
    ) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.status = status;
        this.roles = List.copyOf(roles);
    }

    public static PetUserPrincipal from(UserEntity user) {
        return new PetUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getName(),
                user.getPhone(),
                user.getStatus(),
                user.getRoles().stream().map(role -> role.getCode()).sorted().toList()
        );
    }

    public UUID userId() {
        return userId;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    public String phone() {
        return phone;
    }

    public List<String> roles() {
        return roles;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
