package com.claimpilot.security;

import java.time.Instant;
import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.claimpilot.user.AppUser;
import com.claimpilot.user.CurrentUserService;
import com.claimpilot.user.UserRepository;
import com.claimpilot.user.UserResponse;

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

    /** Self-service sign-up, so anyone can try the demo with their own (fictional) documents. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("The username " + username + " is already taken.");
        }
        AppUser user = users.save(new AppUser(username, request.displayName().strip(),
                passwordEncoder.encode(request.password())));
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(currentUser.get());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "may contain only letters, digits, dots, dashes and underscores") String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
    }
}
