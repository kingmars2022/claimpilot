package com.claimpilot.claim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimDraftRepository extends JpaRepository<ClaimDraft, UUID> {

    List<ClaimDraft> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable page);

    Optional<ClaimDraft> findByIdAndOwnerId(UUID id, Long ownerId);
}
