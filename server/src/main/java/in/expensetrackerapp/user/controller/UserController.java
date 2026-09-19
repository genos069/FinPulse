package in.expensetrackerapp.user.controller;

import in.expensetrackerapp.user.dto.UserResponse;
import in.expensetrackerapp.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Principal principal, Authentication authentication) {
        String userId = principal != null ? principal.getName() : authentication.getName();
        UserResponse response = userService.getUserProfileById(userId);
        return ResponseEntity.ok(response);
    }
}
