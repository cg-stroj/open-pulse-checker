package io.openpulsechecker.apikey;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceApiKeyRepository extends JpaRepository<ServiceApiKeyEntity, UUID> {
    Optional<ServiceApiKeyEntity> findByKeyId(String keyId);
}
