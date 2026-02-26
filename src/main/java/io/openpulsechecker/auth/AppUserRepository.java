package io.openpulsechecker.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {
    Optional<AppUserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
