package com.companybrain.user;

public record DepartmentResponse(Long id, String code, String name) {

    public static DepartmentResponse from(Department department) {
        return department == null ? null
                : new DepartmentResponse(department.getId(), department.getCode(), department.getName());
    }
}
