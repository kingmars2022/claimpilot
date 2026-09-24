package com.claimpilot.user;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Loads the signed-in user from the database on every request, so a deleted account stops
 * working at once even if its token has not expired.
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
                .filter(user -> !user.isDeletionPending())
                .orElseThrow(() -> new InsufficientAuthenticationException("This account no longer exists."));
    }
}
