package io.openpulsechecker.audit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthAuditListener {

    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public AuthAuditListener(AuditService auditService, MeterRegistry meterRegistry) {
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            String uri = attr.getRequest().getRequestURI();
            if (uri != null && uri.endsWith("/admin/auth/login")) {
                auditService.log(username, "AUTH_LOGIN", "auth/basic", "SUCCESS", null);
            }
        }
        meterRegistry.counter("openpulse.auth.success").increment();
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        // For failures, we log all to detect brute-force attempts on any endpoint
        auditService.log(username, "AUTH_LOGIN", "auth/basic", "FAILURE", event.getException().getMessage());
        meterRegistry.counter("openpulse.auth.failures").increment();
    }
}
