package com.companybrain.user;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only department list for any signed-in user (used by the document visibility picker). */
@RestController
public class DepartmentController {

    private final DepartmentRepository departments;

    public DepartmentController(DepartmentRepository departments) {
        this.departments = departments;
    }

    @GetMapping("/api/departments")
    public List<DepartmentResponse> list() {
        return departments.findAllByOrderByNameAsc().stream().map(DepartmentResponse::from).toList();
    }
}
