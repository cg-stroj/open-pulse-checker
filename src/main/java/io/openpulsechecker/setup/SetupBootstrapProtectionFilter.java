package io.openpulsechecker.setup;

import io.openpulsechecker.audit.AuditService;
import io.openpulsechecker.auth.UserRoleRepository;
import io.openpulsechecker.setup.SetupStateEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SetupBootstrapProtectionFilter extends OncePerRequestFilter {

    private static final int SETUP_STATE_SINGLETON_ID = 1;
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SETUP_SECRET_HEADER = "X-Setup-Bootstrap-Secret";

    private final SetupProperties setupProperties;
    private final SetupStateRepository setupStateRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public SetupBootstrapProtectionFilter(
            SetupProperties setupProperties,
            SetupStateRepository setupStateRepository,
            UserRoleRepository userRoleRepository,
            AuditService auditService
    ) {
        this.setupProperties = setupProperties;
        this.setupStateRepository = setupStateRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresProtection(request) || !isSetupOpen()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isAllowedBySecret(request) || isAllowedByCidr(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        auditService.log("SETUP_BOOTSTRAP_DENIED", "setup/bootstrap", "FAILURE",
                "Denied setup bootstrap access from " + clientIp + " to " + request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"setup bootstrap access denied\"}");
    }

    private boolean requiresProtection(HttpServletRequest request) {
        if (!setupProperties.bootstrapProtectionEnabled()) {
            return false;
        }
        if (!"/api/v1/setup/status".equals(request.getRequestURI())
                && !"/api/v1/setup/first-admin".equals(request.getRequestURI())) {
            return false;
        }
        String method = request.getMethod();
        return "GET".equals(method) || "POST".equals(method);
    }

    private boolean isSetupOpen() {
        SetupStateEntity state = setupStateRepository.findById(SETUP_STATE_SINGLETON_ID).orElse(null);
        if (state != null && state.isSetupLocked()) {
            return false;
        }
        return !userRoleRepository.existsByRoleName(ADMIN_ROLE);
    }

    private boolean isAllowedBySecret(HttpServletRequest request) {
        String configuredSecret = trimToNull(setupProperties.bootstrapSecret());
        if (configuredSecret == null) {
            return false;
        }
        String providedSecret = trimToNull(request.getHeader(SETUP_SECRET_HEADER));
        return configuredSecret.equals(providedSecret);
    }

    private boolean isAllowedByCidr(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        List<String> allowedCidrs = setupProperties.bootstrapAllowedCidrs();
        if (allowedCidrs == null || allowedCidrs.isEmpty()) {
            return false;
        }
        return allowedCidrs.stream()
                .map(this::trimToNull)
                .filter(cidr -> cidr != null)
                .anyMatch(cidr -> new IpAddressMatcher(cidr).matches(clientIp));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = trimToNull(request.getHeader("X-Forwarded-For"));
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
