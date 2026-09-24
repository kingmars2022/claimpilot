package com.claimpilot.user;

import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileRepository profiles;
    private final CurrentUserService currentUser;

    public ProfileController(ProfileRepository profiles, CurrentUserService currentUser) {
        this.profiles = profiles;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ProfileDto get() {
        return profiles.findById(currentUser.get().getId()).map(ProfileDto::from).orElse(ProfileDto.empty());
    }

    @PutMapping
    @Transactional
    public ProfileDto update(@Valid @RequestBody ProfileDto request) {
        Long userId = currentUser.get().getId();
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        profile.update(request);
        return ProfileDto.from(profiles.save(profile));
    }
}
