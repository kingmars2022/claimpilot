package com.claimpilot.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * The user's activity log: sign-ins, uploads, processing results, claims and downloads. It joins
 * the caller's transaction, so an action that is rolled back is not logged as done.
 */
@Service
public class AuditService {

    private static final int PAGE = 100;

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public record Entry(AuditAction action, String targetType, String targetId, String detail, Instant at) {
    }

    public void record(Long userId, AuditAction action, String targetType, Object targetId, String detail) {
        repository.save(new AuditEvent(userId, action, targetType, targetId == null ? null : targetId.toString(),
                detail));
    }

    public List<Entry> recent(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, PAGE)).stream()
                .map(e -> new Entry(e.getAction(), e.getTargetType(), e.getTargetId(), e.getDetail(), e.getCreatedAt()))
                .toList();
    }
}
