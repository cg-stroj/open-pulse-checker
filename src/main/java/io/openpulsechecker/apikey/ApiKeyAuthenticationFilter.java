package io.openpulsechecker.apikey;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ServiceApiKeyRepository repository;
    private final ApiKeyHasher hasher;
    private final MeterRegistry meterRegistry;

    public ApiKeyAuthenticationFilter(ServiceApiKeyRepository repository, ApiKeyHasher hasher, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.hasher = hasher;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("X-API-Key");
        if (header != null && !header.isBlank()) {
            String[] parts = header.split("\\.", 2);
            if (parts.length == 2) {
                var key = repository.findByKeyId(parts[0]).orElse(null);
                if (key != null && key.isEnabled() && key.getRevokedAt() == null) {
                    String hashed = hasher.hash(parts[1]);
                    if (hashed.equals(key.getSecretHash())) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                "svc:" + key.getKeyId(),
                                "N/A",
                                List.of(new SimpleGrantedAuthority("ROLE_" + key.getRoleName())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        meterRegistry.counter("openpulse.auth.failures").increment();
                    }
                } else {
                    meterRegistry.counter("openpulse.auth.failures").increment();
                }
            } else {
                meterRegistry.counter("openpulse.auth.failures").increment();
            }
        }

        filterChain.doFilter(request, response);
    }
}
