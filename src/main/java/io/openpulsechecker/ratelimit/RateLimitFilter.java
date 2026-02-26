package io.openpulsechecker.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter rateLimiter;

    public RateLimitFilter(InMemoryRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isSensitive(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = buildKey(request);
        RateLimitDecision decision = rateLimiter.tryConsume(key);
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSensitive(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/auth")) return true;
        if (!uri.startsWith("/api/v1/monitors")) return false;
        if ("POST".equals(method) || "PATCH".equals(method)) return true;
        return "POST".equals(method) && uri.endsWith("/run-check");
    }

    private String buildKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return "principal:" + auth.getName() + ":" + request.getRequestURI();
        }
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            String keyId = apiKey.split("\\.")[0];
            return "apikey:" + keyId + ":" + request.getRequestURI();
        }
        return "ip:" + request.getRemoteAddr() + ":" + request.getRequestURI();
    }
}
