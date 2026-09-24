package com.claimpilot.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.claimpilot.config.AppProperties;

/**
 * The user's activity log: sign-ins, uploads, processing results, claims and downloads. It joins
 * the caller's transaction, so an action that is rolled back is not logged as done.
 */
@Service
public class AuditService {

    private static final int PAGE = 100;

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final Duration retention;

    public AuditService(AuditEventRepository repository, AppProperties properties) {
        this.repository = repository;
        this.retention = properties.audit().retention();
    }

    /** Every night: entries older than the retention period are deleted. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public int purgeExpired() {
        int deleted = repository.deleteOlderThan(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} activity log entries older than {}", deleted, retention);
        }
        return deleted;
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
