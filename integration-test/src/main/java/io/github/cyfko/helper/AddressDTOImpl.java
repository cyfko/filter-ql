package io.github.cyfko.helper;


import io.github.cyfko.AddressDTO;

public record AddressDTOImpl(
        Long id,
        String street,
        String city,
        String zipCode,
        String country
) implements AddressDTO {

    // Implémentation des méthodes de l'interface
    @Override public Long getId() { return id; }
    @Override public String getStreet() { return street; }
    @Override public String getCity() { return city; }
    @Override public String getZipCode() { return zipCode; }
    @Override public String getCountry() { return country; }
}