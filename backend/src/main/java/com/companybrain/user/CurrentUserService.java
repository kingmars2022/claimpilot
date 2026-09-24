package com.companybrain.user;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Loads the signed-in user from the database on every request. The token only proves who the
 * user is; role and department are read fresh, so a permission change applies immediately
 * instead of when the token expires.
 */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public AppUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Not signed in.");
        }
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new InsufficientAuthenticationException("This account no longer exists."));
    }
}
