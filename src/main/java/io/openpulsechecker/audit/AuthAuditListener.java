package io.openpulsechecker.audit;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

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
        auditService.log(username, "AUTH_LOGIN", "auth/basic", "SUCCESS", null);
        meterRegistry.counter("openpulse.auth.success").increment();
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        auditService.log(username, "AUTH_LOGIN", "auth/basic", "FAILURE", event.getException().getMessage());
        meterRegistry.counter("openpulse.auth.failures").increment();
    }
}
