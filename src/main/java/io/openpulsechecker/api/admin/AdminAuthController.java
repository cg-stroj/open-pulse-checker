package io.openpulsechecker.api.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    @GetMapping("/login")
    public Map<String, String> login(Principal principal) {
        return Map.of("username", principal != null ? principal.getName() : "anonymous");
    }
}
