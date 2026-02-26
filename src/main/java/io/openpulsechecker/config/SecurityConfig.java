package io.openpulsechecker.config;

import io.openpulsechecker.apikey.ApiKeyAuthenticationFilter;
import io.openpulsechecker.apikey.ApiKeyBootstrapProperties;
import io.openpulsechecker.auth.AdminBootstrapProperties;
import io.openpulsechecker.ratelimit.RateLimitFilter;
import io.openpulsechecker.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableConfigurationProperties({
        SecurityProperties.class,
        AdminBootstrapProperties.class,
        ApiKeyBootstrapProperties.class,
        RateLimitProperties.class
})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                   RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/status-pages/**").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitors").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/monitors/*/enabled").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitors/*/run-check").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/status-pages/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/status-pages/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/status-pages/**").hasRole("ADMIN")
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
