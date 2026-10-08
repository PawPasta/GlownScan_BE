package com.pawpasta.glowscan_be.profile.controller.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.pawpasta.glowscan_be.profile.domain.enums.Gender;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class UpdatePersonalProfileRequest {

    @Size(max = 150, message = "Full name must not exceed 150 characters")
    @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL, message = "Full name cannot be blank")
    private String fullName;

    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dateOfBirth;

    private Gender gender;

    private boolean fullNamePresent;
    private boolean dateOfBirthPresent;
    private boolean genderPresent;

    public String getFullName() {
        return fullName;
    }

    @JsonSetter("fullName")
    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.fullNamePresent = true;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    @JsonSetter("dateOfBirth")
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        this.dateOfBirthPresent = true;
    }

    public Gender getGender() {
        return gender;
    }

    @JsonSetter("gender")
    public void setGender(Gender gender) {
        this.gender = gender;
        this.genderPresent = true;
    }

    public boolean hasFullName() {
        return fullNamePresent;
    }

    public boolean hasDateOfBirth() {
        return dateOfBirthPresent;
    }

    public boolean hasGender() {
        return genderPresent;
    }

    public boolean hasUpdates() {
        return fullNamePresent || dateOfBirthPresent || genderPresent;
    }
}
