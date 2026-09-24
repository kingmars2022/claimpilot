package com.claimpilot.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    private String targetType;
    private String targetId;
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
        // for JPA
    }

    AuditEvent(Long userId, AuditAction action, String targetType, String targetId, String detail) {
        this.userId = userId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail == null || detail.length() <= 300 ? detail : detail.substring(0, 297) + "...";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
