package io.github.cyfko.example;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Provider;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Projection(
        entity = Person.class,
        providers = {
                @Provider(VirtualResolverConfig.class),
                @Provider(UserTenancyService.class)
        }
)
@Exposure(
        value = "users",
        basePath = "/api/v1"
)
public class PersonDTO {

    private Long id;

    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.NE, Op.IN})
    private String username;

    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES, Op.NE})
    private String email;

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String firstName;

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN, Op.IS_NULL})
    private String lastName;

    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    private Integer age;

    @ExposedAs(value = "ACTIVE", operators = {Op.EQ})
    private Boolean active;

    @ExposedAs(value = "REGISTERED_AT", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    private LocalDateTime registeredAt;

    @ExposedAs(value = "BIRTH_DATE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    private LocalDate birthDate;

    private AddressDTO address;

    // Constructors
    public PersonDTO() {
    }

    public PersonDTO(Long id, String username, String email, String firstName, String lastName,
                     Integer age, Boolean active, LocalDateTime registeredAt, LocalDate birthDate) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.active = active;
        this.registeredAt = registeredAt;
        this.birthDate = birthDate;
    }

    /**
     * Virtual field: Full name (static method)
     * Searches in both first name and last name fields.
     */
    @ExposedAs(
            value = "FULL_NAME",
            operators = {Op.MATCHES}
    )
    public static PredicateResolverMapping<Person> fullNameMatches() {
        return (root, query, cb, params) -> {
            if (params.length == 0) return cb.conjunction();

            String searchTerm = (String) params[0];
            String pattern = "%" + searchTerm + "%";
            Predicate firstName = cb.like(root.get("firstName"), pattern);
            Predicate lastName = cb.like(root.get("lastName"), pattern);
            return cb.or(firstName, lastName);
        };
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public AddressDTO getAddress() {
        return address;
    }

    public void setAddress(AddressDTO address) {
        this.address = address;
    }

    @PreAuthorize("hasAuthority('USER')")
    @Cacheable(value = "userSearchCache", key = "#filterRequest.hashCode()")
    public void searchEndpoint(){}
}
