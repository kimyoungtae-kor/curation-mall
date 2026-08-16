package kr.co.petcuration.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    protected RoleEntity() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
