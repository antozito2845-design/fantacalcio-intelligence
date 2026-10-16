package it.fantacalciointelligence.backend.config;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserProvisioningController {

    private final UserProvisioningService service;

    public UserProvisioningController(UserProvisioningService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionedUserResponse create(@Valid @RequestBody ProvisionUserRequest request) {
        return service.create(request);
    }
}
