package io.openpulsechecker.auth;

import io.openpulsechecker.setup.SetupStateRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    private static final int SETUP_STATE_SINGLETON_ID = 1;

    private final AdminBootstrapProperties properties;
    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final SetupStateRepository setupStateRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapInitializer(
            AdminBootstrapProperties properties,
            AppUserRepository appUserRepository,
            UserRoleRepository userRoleRepository,
            SetupStateRepository setupStateRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.setupStateRepository = setupStateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (!properties.emergencyFallbackEnabled()) {
            log.info("Bootstrap admin is configured but emergency fallback flag is disabled; skipping bootstrap admin initialization");
            return;
        }
        if (isSetupLocked() || userRoleRepository.existsByRoleName("ADMIN")) {
            log.warn("Bootstrap admin emergency fallback is blocked because setup is already completed");
            return;
        }
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("bootstrap-admin username/password are required when enabled");
        }
        if (appUserRepository.existsByUsername(properties.username().trim())) {
            return;
        }

        AppUserEntity admin = new AppUserEntity();
        admin.setUsername(properties.username().trim());
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setEnabled(true);
        AppUserEntity saved = appUserRepository.save(admin);

        UserRoleEntity role = new UserRoleEntity();
        role.setUserId(saved.getId());
        role.setRoleName("ADMIN");
        role.setCreatedAt(Instant.now());
        userRoleRepository.save(role);

        log.warn("Bootstrap admin emergency fallback was used to create an admin account; disable it immediately after recovery");
    }

    private boolean isSetupLocked() {
        return setupStateRepository.findById(SETUP_STATE_SINGLETON_ID)
                .map(state -> state.isSetupLocked())
                .orElse(false);
    }
}
