package io.openpulsechecker.auth;

import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final AppUserRepository appUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapInitializer(
            AdminBootstrapProperties properties,
            AppUserRepository appUserRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.appUserRepository = appUserRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()) {
            throw new IllegalStateException("bootstrap-admin username/password are required when enabled");
        }
        if (appUserRepository.existsByUsername(properties.username())) {
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
    }
}
