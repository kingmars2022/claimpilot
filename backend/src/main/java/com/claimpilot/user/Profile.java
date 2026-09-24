package com.claimpilot.user;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Personal details entered once and reused on every claim form. Deliberately has no field for a
 * social insurance number: claim forms that ask for one are left for the user to complete.
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    private Long userId;

    private String fullName;
    private LocalDate dateOfBirth;
    private String street;
    private String city;
    private String province;
    private String postalCode;
    private String phone;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Profile() {
        // for JPA
    }

    public Profile(Long userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public void update(ProfileDto dto) {
        this.fullName = blankToNull(dto.fullName());
        this.dateOfBirth = dto.dateOfBirth();
        this.street = blankToNull(dto.street());
        this.city = blankToNull(dto.city());
        this.province = blankToNull(dto.province());
        this.postalCode = blankToNull(dto.postalCode());
        this.phone = blankToNull(dto.phone());
        this.updatedAt = Instant.now();
    }

    /** One-line postal address, or null when no part of it is filled in. */
    public String address() {
        String cityLine = String.join(" ", nonBlank(city, province, postalCode));
        String joined = String.join(", ", nonBlank(street, cityLine));
        return joined.isBlank() ? null : joined;
    }

    private static java.util.List<String> nonBlank(String... parts) {
        return java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).map(String::strip).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public Long getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public String getProvince() {
        return province;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPhone() {
        return phone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
