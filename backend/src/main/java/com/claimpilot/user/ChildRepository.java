package com.claimpilot.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findByUserIdOrderById(Long userId);

    Optional<Child> findByIdAndUserId(Long id, Long userId);
}
