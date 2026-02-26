package io.openpulsechecker.apikey;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApiKeyBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyBootstrapInitializer.class);

    private final ApiKeyBootstrapProperties properties;
    private final ServiceApiKeyRepository repository;
    private final ApiKeyHasher hasher;
    private final Clock clock;

    public ApiKeyBootstrapInitializer(ApiKeyBootstrapProperties properties,
                                      ServiceApiKeyRepository repository,
                                      ApiKeyHasher hasher,
                                      Clock clock) {
        this.properties = properties;
        this.repository = repository;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) return;
        if (properties.keyId() == null || properties.keyId().isBlank()
                || properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("bootstrap-api-key key-id/secret are required when enabled");
        }
        if (repository.findByKeyId(properties.keyId()).isPresent()) return;

        ServiceApiKeyEntity entity = new ServiceApiKeyEntity();
        entity.setKeyId(properties.keyId().trim());
        entity.setSecretHash(hasher.hash(properties.secret()));
        entity.setRoleName((properties.role() == null || properties.role().isBlank()) ? "ADMIN" : properties.role().trim());
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now(clock));
        repository.save(entity);

        log.warn("Bootstrapped API key {} with role {} (secret never stored). Rotate immediately.", entity.getKeyId(), entity.getRoleName());
    }
}
