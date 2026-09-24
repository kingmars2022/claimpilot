package com.companybrain.admin;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.companybrain.user.CurrentUserService;
import com.companybrain.user.DepartmentResponse;
import com.companybrain.user.UserResponse;

/** User and department management. Restricted to ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;
    private final CurrentUserService currentUser;

    public AdminController(AdminService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/users")
    public List<UserResponse> users() {
        return service.listUsers();
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminDtos.CreateUser request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(request));
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminDtos.UpdateUser request) {
        return service.updateUser(id, request, currentUser.get());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        service.deleteUser(id, currentUser.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody AdminDtos.CreateDepartment request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createDepartment(request));
    }
}
