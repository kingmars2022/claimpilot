package com.claimpilot.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    /** Set when the member asked for the account to be deleted; the account no longer works. */
    private Instant deletionRequestedAt;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String displayName, String passwordHash) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isDeletionPending() {
        return deletionRequestedAt != null;
    }

    public void requestDeletion() {
        if (deletionRequestedAt == null) {
            deletionRequestedAt = Instant.now();
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
