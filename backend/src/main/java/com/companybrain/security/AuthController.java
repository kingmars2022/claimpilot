package com.companybrain.security;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.companybrain.user.AppUser;
import com.companybrain.user.CurrentUserService;
import com.companybrain.user.UserRepository;
import com.companybrain.user.UserResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final CurrentUserService currentUser;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                          CurrentUserService currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Same message for unknown user and wrong password, so usernames cannot be probed.
        AppUser user = users.findByUsername(request.username().strip())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.get());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
    }
}
