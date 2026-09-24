package com.claimpilot.claim;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FormFieldMappingRepository extends JpaRepository<FormFieldMapping, Long> {

    List<FormFieldMapping> findByTemplateSha256(String templateSha256);
}
