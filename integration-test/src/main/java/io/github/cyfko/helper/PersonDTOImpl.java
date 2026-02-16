package io.github.cyfko.helper;

import io.github.cyfko.PersonDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PersonDTOImpl(
    Long id,
    String username,
    String email,
    String firstName,
    String lastName,
    Integer age,
    Boolean active,
    LocalDateTime registeredAt,
    LocalDate birthDate
) implements PersonDTO {

    @Override public Long getId() { return id; }
    @Override public String getUsername() { return username; }
    @Override public String getEmail() { return email; }
    @Override public String getFirstName() { return firstName; }
    @Override public String getLastName() { return lastName; }
    @Override public Integer getAge() { return age; }
    @Override public Boolean isActive() { return active; }
    @Override public LocalDateTime getRegisteredAt() { return registeredAt; }
    @Override public LocalDate getBirthDate() { return birthDate; }
}