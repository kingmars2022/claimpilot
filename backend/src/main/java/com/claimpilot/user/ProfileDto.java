package com.claimpilot.user;

import java.time.LocalDate;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record ProfileDto(
        @Size(max = 150) String fullName,
        @Past LocalDate dateOfBirth,
        @Size(max = 200) String street,
        @Size(max = 100) String city,
        @Size(max = 50) String province,
        @Size(max = 20) String postalCode,
        @Size(max = 40) String phone,
        @Size(max = 150) String spouseName,
        @Past LocalDate spouseDateOfBirth,
        Custody custody,
        @Size(max = 150) String otherParentName,
        @Past LocalDate otherParentDateOfBirth) {

    public static ProfileDto from(Profile p) {
        return new ProfileDto(p.getFullName(), p.getDateOfBirth(), p.getStreet(), p.getCity(), p.getProvince(),
                p.getPostalCode(), p.getPhone(), p.getSpouseName(), p.getSpouseDateOfBirth(), p.getCustody(), p.getOtherParentName(),
                p.getOtherParentDateOfBirth());
    }

    public static ProfileDto empty() {
        return new ProfileDto(null, null, null, null, null, null, null, null, null, Custody.TOGETHER, null, null);
    }
}
