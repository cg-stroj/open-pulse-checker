package io.openpulsechecker.setup;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public SetupStatusResponse status() {
        return setupService.status();
    }

    @PostMapping("/first-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateFirstAdminResponse createFirstAdmin(@Valid @RequestBody CreateFirstAdminRequest request) {
        return setupService.createFirstAdmin(request);
    }
}
