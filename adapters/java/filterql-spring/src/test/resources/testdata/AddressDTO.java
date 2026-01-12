package io.github.cyfko.example;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.projection.MethodReference;
import io.github.cyfko.projection.Projection;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * DTO for Address entity.
 */
@Projection(from = Address.class)
@Exposure(
        value = "addresses",
        basePath = "/api/v1",
        annotationsFrom = @MethodReference()
)
public class AddressDTO {
    private Long id;

    @ExposedAs(value = "STREET", operators = {Op.EQ, Op.MATCHES, Op.NE})
    private String street;

    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String city;

    @ExposedAs(value = "ZIP_CODE", operators = {Op.EQ, Op.MATCHES})
    private String zipCode;

    @ExposedAs(value = "COUNTRY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String country;

    // Constructors
    public AddressDTO() {
    }

    public AddressDTO(Long id, String street, String city, String zipCode, String country) {
        this.id = id;
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
        this.country = country;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    private static void searchEndpoint(){}
}
