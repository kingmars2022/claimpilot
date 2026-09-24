package com.claimpilot.user;

/** Published after an account and its data are deleted, so in-memory traces are dropped too. */
public record AccountDeleted(Long userId) {
}
