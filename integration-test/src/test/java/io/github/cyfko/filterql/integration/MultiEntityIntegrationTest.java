package io.github.cyfko.filterql.integration;

import io.github.cyfko.*;
import io.github.cyfko.filterql.TestSecurityConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.core.api.PropertyReference;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = io.github.cyfko.Main.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class MultiEntityIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PersonRepository personRepository;
    @Autowired private AddressRepository addressRepository;

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();
        addressRepository.deleteAll();
    }

    @Test
    void shouldGeneratePropertyRefEnumsForBothEntities() throws Exception {
        assertTrue(Class.forName("io.github.cyfko.PersonDTO_").isEnum());
        assertTrue(Class.forName("io.github.cyfko.AddressDTO_").isEnum());
    }

    @Test
    void shouldGenerateCorrectAddressPropertyRefConstants() throws Exception {
        Class<?> AddressDTO_ = Class.forName("io.github.cyfko.AddressDTO_");
        Object[] constants = AddressDTO_.getEnumConstants();
        assertEquals(5, constants.length);
        for (String name : List.of("ID", "STREET", "CITY", "ZIP_CODE", "COUNTRY")) {
            assertDoesNotThrow(() -> Enum.valueOf((Class<Enum>) AddressDTO_, name));
        }
    }

    @Test
    void shouldGenerateEndpointsForBothEntities() throws Exception {
        Class<?> controllerClass = Class.forName("io.github.cyfko.filterql.spring.controller.FilterQlController");
        boolean hasSearchPerson = false, hasSearchAddress = false;
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (method.getName().equals("searchPersonDTO")) hasSearchPerson = true;
            if (method.getName().equals("searchAddressDTO")) hasSearchAddress = true;
        }
        assertTrue(hasSearchPerson);
        assertTrue(hasSearchAddress);
    }

    @Test
    void shouldSearchPersonsSuccessfully() {
        createTestPerson("john", "john@example.com", "John", "Doe", 30);
        createTestPerson("jane", "jane@example.com", "Jane", "Smith", 25);

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.USERNAME, Op.EQ, "john")
                .combineWith("f")
                .build();

        PaginatedData<Map<String, Object>> result = postSearch("/api/v1/search/users", request, (Class<Map<String, Object>>) (Class<?>) Map.class);
        assertEquals(1, result.data().size());
        assertEquals("john", result.data().get(0).get("username"));
    }

    @Test
    void shouldSearchAddressesSuccessfully() {
        createTestAddress("123 Main St", "Paris", "75001", "France");
        createTestAddress("456 Oak Ave", "London", "SW1A", "UK");

        FilterRequest<AddressDTO_> request = FilterRequest.<AddressDTO_>builder()
                .filter("f", AddressDTO_.CITY, Op.EQ, "Paris")
                .combineWith("f")
                .build();

        PaginatedData<Map<String,Object>> result = postSearch("/api/v1/search/addresses", request, (Class<Map<String, Object>>) (Class<?>) Map.class);
        assertEquals(1, result.data().size());
        assertEquals("Paris", result.data().get(0).get("city"));
    }

    @Test
    void shouldAllowSimultaneousQueriesOnBothEntities() {
        createTestPerson("john", "john@example.com", "John", "Doe", 30);
        createTestAddress("123 Main St", "Paris", "75001", "France");

        PaginatedData<PersonDTO> personPage = postSearch("/api/v1/search/users", FilterRequest.builder().build(), PersonDTO.class);
        PaginatedData<AddressDTO> addressPage = postSearch("/api/v1/search/addresses", FilterRequest.builder().build(), AddressDTO.class);

        assertEquals(1, personPage.data().size());
        assertEquals(1, addressPage.data().size());
    }

    @Test
    void shouldFilterAddressByCityIn() {
        createTestAddress("123 Main St", "Paris", "75001", "France");
        createTestAddress("456 Oak Ave", "London", "SW1A", "UK");
        createTestAddress("789 Elm St", "Berlin", "10115", "Germany");

        FilterRequest<AddressDTO_> request = FilterRequest.<AddressDTO_>builder()
                .filter("f", AddressDTO_.CITY, Op.IN, List.of("Paris", "London"))
                .combineWith("f")
                .build();

        PaginatedData<Map<String, Object>> result = postSearch("/api/v1/search/addresses", request, (Class<Map<String, Object>>) (Class<?>) Map.class);
        assertEquals(2, result.data().size());
        assertTrue(result.data().stream().anyMatch(a -> a.get("city").equals("Paris")));
        assertTrue(result.data().stream().anyMatch(a -> a.get("city").equals("London")));
    }

    @Test
    void shouldFilterAddressByStreetMatches() {
        createTestAddress("123 Main Street", "Paris", "75001", "France");
        createTestAddress("456 Main Avenue", "London", "SW1A", "UK");
        createTestAddress("789 Oak Boulevard", "Berlin", "10115", "Germany");

        FilterRequest<AddressDTO_> request = FilterRequest.<AddressDTO_>builder()
                .filter("f", AddressDTO_.STREET, Op.MATCHES, "%Main%")
                .combineWith("f")
                .build();

        PaginatedData<AddressDTO> result = postSearch("/api/v1/search/addresses", request, AddressDTO.class);
        assertEquals(2, result.data().size());
    }

    // Helper: generic POST search
    private <T, E extends Enum<E> & PropertyReference> PaginatedData<T> postSearch(String url, FilterRequest<E> request, Class<T> dtoClass) {
        ResponseEntity<PaginatedData<T>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request, new HttpHeaders() {{
                    setContentType(MediaType.APPLICATION_JSON);
                }}),
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    // Helpers: entity creation
    private Person createTestPerson(String username, String email, String firstName, String lastName, Integer age) {
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(email);
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setAge(age);
        person.setActive(true);
        person.setRegisteredAt(LocalDateTime.now());
        person.setBirthDate(LocalDate.now().minusYears(age));
        return personRepository.saveAndFlush(person);
    }

    private Address createTestAddress(String street, String city, String zipCode, String country) {
        Address address = new Address();
        address.setStreet(street);
        address.setCity(city);
        address.setZipCode(zipCode);
        address.setCountry(country);
        return addressRepository.saveAndFlush(address);
    }
}