package kr.co.petcuration.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import kr.co.petcuration.identity.domain.UserStatus;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "normalized_email", nullable = false, unique = true, length = 320)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<RoleEntity> roles = new LinkedHashSet<>();

    protected UserEntity() {
    }

    public UserEntity(
            UUID id,
            String email,
            String normalizedEmail,
            String passwordHash,
            String name,
            String phone,
            RoleEntity role,
            Instant now
    ) {
        this.id = id;
        this.email = email;
        this.normalizedEmail = normalizedEmail;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.roles.add(role);
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<RoleEntity> getRoles() {
        return Set.copyOf(roles);
    }
}
