package com.claimpilot.user;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** A child as shown and edited in the profile; {@code id} is null for a child being added. */
public record ChildDto(
        Long id,
        @NotBlank @Size(max = 150) String fullName,
        @Past LocalDate dateOfBirth,
        Custody custody,
        @Size(max = 150) String otherParentName,
        @Past LocalDate otherParentDateOfBirth) {

    public static ChildDto from(Child c) {
        return new ChildDto(c.getId(), c.getFullName(), c.getDateOfBirth(), c.getCustody(), c.getOtherParentName(),
                c.getOtherParentDateOfBirth());
    }
}
