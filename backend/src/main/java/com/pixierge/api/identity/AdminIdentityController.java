package com.pixierge.api.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminIdentityController {

    private final UserRepository userRepository;

    public AdminIdentityController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users")
    List<UserSummaryResponse> users() {
        return userRepository.listUsers();
    }

    @GetMapping("/roles")
    List<RoleSummaryResponse> roles() {
        return userRepository.listRoles();
    }
}
