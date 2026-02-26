package io.openpulsechecker.config;

import io.openpulsechecker.auth.AdminBootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, AdminBootstrapProperties.class})
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
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
