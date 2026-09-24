package com.claimpilot.audit;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimpilot.user.CurrentUserService;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;
    private final CurrentUserService currentUser;

    public AuditController(AuditService audit, CurrentUserService currentUser) {
        this.audit = audit;
        this.currentUser = currentUser;
    }

    /** The signed-in user's last 100 recorded actions, newest first. */
    @GetMapping
    public List<AuditService.Entry> recent() {
        return audit.recent(currentUser.get().getId());
    }
}
