package io.openpulsechecker.setup;

import io.openpulsechecker.apikey.ApiKeyHasher;
import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.auth.AppUserEntity;
import io.openpulsechecker.auth.AppUserRepository;
import io.openpulsechecker.auth.UserRoleEntity;
import io.openpulsechecker.auth.UserRoleRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {

    private static final int SETUP_STATE_SINGLETON_ID = 1;
    private static final String ADMIN_ROLE = "ADMIN";

    private final SetupStateRepository setupStateRepository;
    private final SetupTokenRepository setupTokenRepository;
    private final UserRoleRepository userRoleRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiKeyHasher apiKeyHasher;
    private final SetupProperties setupProperties;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SetupService(
            SetupStateRepository setupStateRepository,
            SetupTokenRepository setupTokenRepository,
            UserRoleRepository userRoleRepository,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            ApiKeyHasher apiKeyHasher,
            SetupProperties setupProperties,
            AuditService auditService,
            Clock clock
    ) {
        this.setupStateRepository = setupStateRepository;
        this.setupTokenRepository = setupTokenRepository;
        this.userRoleRepository = userRoleRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.apiKeyHasher = apiKeyHasher;
        this.setupProperties = setupProperties;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SetupStatusResponse status() {
        SetupStateEntity state = getOrCreateLockedState();
        if (isSetupLocked(state)) {
            return new SetupStatusResponse(false, true, null, null);
        }

        Instant now = Instant.now(clock);
        String token = generateToken();
        SetupTokenEntity tokenEntity = new SetupTokenEntity();
        tokenEntity.setTokenHash(apiKeyHasher.hash(token));
        tokenEntity.setExpiresAt(now.plusSeconds(Math.max(1, setupProperties.tokenTtlSeconds())));
        setupTokenRepository.save(tokenEntity);

        auditService.log("SETUP_TOKEN_ISSUE", "setup/first-admin", "SUCCESS", "Issued one-time setup token");
        return new SetupStatusResponse(true, false, token, tokenEntity.getExpiresAt());
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class, SetupLockedException.class})
    public CreateFirstAdminResponse createFirstAdmin(CreateFirstAdminRequest request) {
        SetupStateEntity state = getOrCreateLockedState();
        if (isSetupLocked(state)) {
            auditService.log("SETUP_FIRST_ADMIN", "setup/first-admin", "FAILURE", "Setup already locked");
            throw new SetupLockedException("Setup is already completed");
        }

        String tokenHash = apiKeyHasher.hash(request.setupToken());
        SetupTokenEntity tokenEntity = setupTokenRepository.findByTokenHash(tokenHash).orElse(null);
        Instant now = Instant.now(clock);
        if (tokenEntity == null || tokenEntity.getConsumedAt() != null || now.isAfter(tokenEntity.getExpiresAt())) {
            auditService.log("SETUP_FIRST_ADMIN", "setup/first-admin", "FAILURE", "Invalid or expired setup token");
            throw new IllegalArgumentException("Invalid or expired setup token");
        }

        if (appUserRepository.existsByUsername(request.username().trim())) {
            auditService.log("SETUP_FIRST_ADMIN", "setup/first-admin", "FAILURE", "Username already exists");
            throw new IllegalArgumentException("Username already exists");
        }

        AppUserEntity user = new AppUserEntity();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        AppUserEntity saved = appUserRepository.save(user);

        UserRoleEntity role = new UserRoleEntity();
        role.setUserId(saved.getId());
        role.setRoleName(ADMIN_ROLE);
        role.setCreatedAt(now);
        userRoleRepository.save(role);

        tokenEntity.setConsumedAt(now);
        setupTokenRepository.save(tokenEntity);

        state.setSetupLocked(true);
        state.setUpdatedAt(now);
        setupStateRepository.save(state);

        auditService.log(saved.getUsername(), "SETUP_FIRST_ADMIN", "setup/first-admin", "SUCCESS", "First admin created");
        return new CreateFirstAdminResponse(saved.getUsername(), ADMIN_ROLE, now);
    }

    private SetupStateEntity getOrCreateLockedState() {
        return setupStateRepository.findById(SETUP_STATE_SINGLETON_ID)
                .orElseGet(() -> {
                    SetupStateEntity state = new SetupStateEntity();
                    state.setId(SETUP_STATE_SINGLETON_ID);
                    state.setSetupLocked(false);
                    state.setUpdatedAt(Instant.now(clock));
                    return setupStateRepository.save(state);
                });
    }

    private boolean isSetupLocked(SetupStateEntity state) {
        if (state.isSetupLocked()) {
            return true;
        }
        boolean hasAdmin = userRoleRepository.existsByRoleName(ADMIN_ROLE);
        if (hasAdmin) {
            state.setSetupLocked(true);
            state.setUpdatedAt(Instant.now(clock));
            setupStateRepository.save(state);
            return true;
        }
        return false;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
