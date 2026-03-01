package io.openpulsechecker.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openpulsechecker.setup.SetupStateEntity;
import io.openpulsechecker.setup.SetupStateRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private SetupStateRepository setupStateRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrapInitializer initializer;

    @Test
    void defaultFallbackDisabledDoesNothing() throws Exception {
        initializer = new AdminBootstrapInitializer(
                new AdminBootstrapProperties(false, false, "admin", "secret"),
                appUserRepository,
                userRoleRepository,
                setupStateRepository,
                passwordEncoder);

        initializer.run(null);

        verify(appUserRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void emergencyFallbackEnabledCreatesBootstrapAdminWhenAllowed() throws Exception {
        initializer = new AdminBootstrapInitializer(
                new AdminBootstrapProperties(true, true, "admin", "secret"),
                appUserRepository,
                userRoleRepository,
                setupStateRepository,
                passwordEncoder);

        when(setupStateRepository.findById(1)).thenReturn(Optional.empty());
        when(userRoleRepository.existsByRoleName("ADMIN")).thenReturn(false);
        when(appUserRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(appUserRepository.save(any(AppUserEntity.class))).thenAnswer(invocation -> {
            AppUserEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        initializer.run(null);

        verify(appUserRepository).save(any(AppUserEntity.class));
        verify(userRoleRepository).save(any(UserRoleEntity.class));
    }

    @Test
    void emergencyFallbackBlockedWhenSetupLocked() throws Exception {
        initializer = new AdminBootstrapInitializer(
                new AdminBootstrapProperties(true, true, "admin", "secret"),
                appUserRepository,
                userRoleRepository,
                setupStateRepository,
                passwordEncoder);

        SetupStateEntity state = new SetupStateEntity();
        state.setId(1);
        state.setSetupLocked(true);
        when(setupStateRepository.findById(1)).thenReturn(Optional.of(state));

        initializer.run(null);

        verify(appUserRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void emergencyFallbackBlockedWhenAdminRoleAlreadyExists() throws Exception {
        initializer = new AdminBootstrapInitializer(
                new AdminBootstrapProperties(true, true, "admin", "secret"),
                appUserRepository,
                userRoleRepository,
                setupStateRepository,
                passwordEncoder);

        when(setupStateRepository.findById(1)).thenReturn(Optional.empty());
        when(userRoleRepository.existsByRoleName("ADMIN")).thenReturn(true);

        initializer.run(null);

        verify(appUserRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }
}
