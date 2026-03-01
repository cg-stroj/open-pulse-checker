package io.openpulsechecker.setup;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SetupTokenRepository extends JpaRepository<SetupTokenEntity, java.util.UUID> {
    Optional<SetupTokenEntity> findByTokenHash(String tokenHash);
}
