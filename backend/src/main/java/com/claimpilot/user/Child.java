package com.claimpilot.user;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A child of the member. Custody is per child, since children of two relationships can have
 * different other parents and arrangements; it decides which plan pays first for that child.
 */
@Entity
@Table(name = "children")
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String fullName;
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Custody custody = Custody.TOGETHER;
    private String otherParentName;
    private LocalDate otherParentDateOfBirth;

    protected Child() {
        // for JPA
    }

    public Child(Long userId, ChildDto dto) {
        this.userId = userId;
        update(dto);
    }

    public void update(ChildDto dto) {
        this.fullName = dto.fullName().strip();
        this.dateOfBirth = dto.dateOfBirth();
        this.custody = dto.custody() == null ? Custody.TOGETHER : dto.custody();
        this.otherParentName = dto.otherParentName() == null || dto.otherParentName().isBlank() ? null
                : dto.otherParentName().strip();
        this.otherParentDateOfBirth = dto.otherParentDateOfBirth();
    }

    public Long getId() {
        return id;
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

    public Custody getCustody() {
        return custody;
    }

    public String getOtherParentName() {
        return otherParentName;
    }

    public LocalDate getOtherParentDateOfBirth() {
        return otherParentDateOfBirth;
    }
}
