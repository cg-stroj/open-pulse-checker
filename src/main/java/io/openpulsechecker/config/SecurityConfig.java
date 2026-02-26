package io.openpulsechecker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitors").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/monitors/*/enabled").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitors/*/run-check").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/monitors/**").hasAnyRole("ADMIN", "VIEWER")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityProperties properties) {
        return new InMemoryUserDetailsManager(
                User.withUsername(properties.adminUsername())
                        .password(normalizePassword(properties.adminPassword()))
                        .roles("ADMIN")
                        .build(),
                User.withUsername(properties.viewerUsername())
                        .password(normalizePassword(properties.viewerPassword()))
                        .roles("VIEWER")
                        .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private String normalizePassword(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("Security credentials must be configured");
        }
        return configured.startsWith("{") ? configured : "{noop}" + configured;
    }
}
