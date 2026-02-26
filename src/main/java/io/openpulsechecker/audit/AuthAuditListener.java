package io.openpulsechecker.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditListener {

    private final AuditService auditService;

    public AuthAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        auditService.log(username, "AUTH_LOGIN", "auth/basic", "SUCCESS", null);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        auditService.log(username, "AUTH_LOGIN", "auth/basic", "FAILURE", event.getException().getMessage());
    }
}
