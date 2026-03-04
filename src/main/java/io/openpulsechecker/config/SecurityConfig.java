package io.openpulsechecker.config;

import io.openpulsechecker.apikey.ApiKeyAuthenticationFilter;
import io.openpulsechecker.apikey.ApiKeyBootstrapProperties;
import io.openpulsechecker.auth.AdminBootstrapProperties;
import io.openpulsechecker.ratelimit.RateLimitFilter;
import io.openpulsechecker.ratelimit.RateLimitProperties;
import io.openpulsechecker.setup.SetupProperties;
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

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableConfigurationProperties({
        SecurityProperties.class,
        AdminBootstrapProperties.class,
        ApiKeyBootstrapProperties.class,
        RateLimitProperties.class,
        SetupProperties.class
})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                   RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/manifest.json").permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/setup/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/status-pages/**").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**", "/api/v2/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitors").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/monitors/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/monitors/*/enabled").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/monitors/*/run-check").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/status-pages/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/status-pages/**").hasRole("ADMIN")
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
