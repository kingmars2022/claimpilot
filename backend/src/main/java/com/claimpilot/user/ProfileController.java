package com.claimpilot.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimpilot.audit.AuditAction;
import com.claimpilot.audit.AuditService;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    static final int MAX_CHILDREN = 12;

    /** The member's children, in the order shown. */
    public record Children(@NotNull @Size(max = MAX_CHILDREN) List<@Valid ChildDto> children) {
    }

    private final ProfileRepository profiles;
    private final ChildRepository children;
    private final CurrentUserService currentUser;
    private final AuditService audit;

    public ProfileController(ProfileRepository profiles, ChildRepository children, CurrentUserService currentUser,
                             AuditService audit) {
        this.audit = audit;
        this.profiles = profiles;
        this.children = children;
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
        audit.record(userId, AuditAction.PROFILE_UPDATED, null, null, null);
        return ProfileDto.from(profiles.save(profile));
    }

    @GetMapping("/children")
    public List<ChildDto> children() {
        return children.findByUserIdOrderById(currentUser.get().getId()).stream().map(ChildDto::from).toList();
    }

    /** Replaces the list: children with an id are updated, new ones added, missing ones removed. */
    @PutMapping("/children")
    @Transactional
    public List<ChildDto> updateChildren(@Valid @RequestBody Children request) {
        Long userId = currentUser.get().getId();
        List<Child> existing = children.findByUserIdOrderById(userId);
        Set<Long> kept = new HashSet<>();
        for (ChildDto dto : request.children()) {
            Child child = dto.id() == null ? null
                    : existing.stream().filter(c -> c.getId().equals(dto.id())).findFirst().orElse(null);
            if (child == null) {
                children.save(new Child(userId, dto));
            } else {
                child.update(dto);
                kept.add(child.getId());
            }
        }
        existing.stream().filter(c -> !kept.contains(c.getId())).forEach(children::delete);
        audit.record(userId, AuditAction.PROFILE_UPDATED, null, null, "children");
        return children();
    }
}
