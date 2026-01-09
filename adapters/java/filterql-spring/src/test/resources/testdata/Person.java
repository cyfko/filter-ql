package io.github.cyfko.example;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Person entity representing a user in the system.
 * <p>
 * This entity demonstrates:
 * - Regular filterable fields (username, email, age, etc.)
 * - Nested relationships (address)
 * - Virtual fields (static and instance methods)
 * - Custom annotations on endpoints (security, caching)
 * - Custom paged result mapper
 */
@Entity
@Table(name = "persons")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Username - unique identifier for the person
     */
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * Email address
     */
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /**
     * First name
     */
    @Column(length = 50)
    private String firstName;

    /**
     * Last name
     */
    @Column(length = 50)
    private String lastName;

    /**
     * Age
     */
    @Column
    private Integer age;

    /**
     * Active status
     */
    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Registration date
     */
    @Column(nullable = false)
    private LocalDateTime registeredAt;

    /**
     * Birth date
     */
    @Column
    private LocalDate birthDate;

    /**
     * Address relationship
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    // Constructors
    public Person() {
        this.registeredAt = LocalDateTime.now();
    }

    public Person(String username, String email, String firstName, String lastName, Integer age) {
        this();
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
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

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}