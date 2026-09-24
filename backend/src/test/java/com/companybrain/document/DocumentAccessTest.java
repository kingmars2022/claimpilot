package com.companybrain.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.companybrain.user.AppUser;
import com.companybrain.user.Department;
import com.companybrain.user.Role;

class DocumentAccessTest {

    private static final Department HR = new Department("HR", "Human Resources");
    private static final Department IT = new Department("IT", "IT");

    @Test
    void documentWithoutDepartmentsIsCompanyWide() {
        assertThat(DocumentAccess.accessList(document(List.of()))).containsExactly("ALL");
    }

    @Test
    void restrictedDocumentListsSortedDepartmentCodes() {
        assertThat(DocumentAccess.accessList(document(List.of(IT, HR)))).containsExactly("HR", "IT");
    }

    @Test
    void employeeSeesCompanyWideAndOwnDepartment() {
        AppUser user = new AppUser("ivan", "Ivan", "hash", Role.EMPLOYEE, IT);
        assertThat(DocumentAccess.filterFor(user).toString()).contains("access").contains("ALL").contains("IT")
                .doesNotContain("HR");
    }

    @Test
    void userWithoutDepartmentSeesOnlyCompanyWide() {
        AppUser user = new AppUser("temp", "Temp", "hash", Role.EMPLOYEE, null);
        assertThat(DocumentAccess.filterFor(user).toString()).contains("ALL").doesNotContain("IT");
    }

    @Test
    void adminIsNotFiltered() {
        AppUser admin = new AppUser("admin", "Admin", "hash", Role.ADMIN, IT);
        assertThat(DocumentAccess.filterFor(admin)).isNull();
    }

    private static KnowledgeDocument document(List<Department> departments) {
        return new KnowledgeDocument("f.md", "text/markdown", 1, "key", departments);
    }
}
