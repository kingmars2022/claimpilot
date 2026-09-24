package com.companybrain.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.companybrain.common.ConflictException;
import com.companybrain.common.NotFoundException;
import com.companybrain.user.AppUser;
import com.companybrain.user.Department;
import com.companybrain.user.DepartmentRepository;
import com.companybrain.user.DepartmentResponse;
import com.companybrain.user.Role;
import com.companybrain.user.UserRepository;
import com.companybrain.user.UserResponse;

@Service
public class AdminService {

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository users, DepartmentRepository departments, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.departments = departments;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> listUsers() {
        return users.findAllByOrderByUsernameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse createUser(AdminDtos.CreateUser request) {
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new ConflictException("The username " + username + " is already taken.");
        }
        AppUser user = new AppUser(username, request.displayName().strip(),
                passwordEncoder.encode(request.password()), request.role(), department(request.departmentId()));
        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse updateUser(Long id, AdminDtos.UpdateUser request, AppUser actingAdmin) {
        AppUser user = user(id);
        if (user.getId().equals(actingAdmin.getId()) && request.role() != Role.ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own admin role.");
        }
        user.update(request.displayName().strip(), request.role(), department(request.departmentId()));
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8) {
                throw new IllegalArgumentException("The new password must be at least 8 characters.");
            }
            user.changePasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long id, AppUser actingAdmin) {
        AppUser user = user(id);
        if (user.getId().equals(actingAdmin.getId())) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }
        users.delete(user);
    }

    @Transactional
    public DepartmentResponse createDepartment(AdminDtos.CreateDepartment request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (departments.existsByCode(code)) {
            throw new ConflictException("A department with code " + code + " already exists.");
        }
        return DepartmentResponse.from(departments.save(new Department(code, request.name().strip())));
    }

    private AppUser user(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " does not exist."));
    }

    private Department department(Long id) {
        if (id == null) {
            return null;
        }
        return departments.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department " + id + " does not exist."));
    }
}
