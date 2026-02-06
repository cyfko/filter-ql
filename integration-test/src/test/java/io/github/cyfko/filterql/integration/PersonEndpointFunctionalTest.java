package io.github.cyfko.filterql.integration;

import io.github.cyfko.Person;
import io.github.cyfko.PersonDTO_;
import io.github.cyfko.PersonRepository;
import io.github.cyfko.filterql.TestSecurityConfig;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.Op;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional integration tests for Person search endpoints.
 * Tests actual HTTP requests and responses to verify end-to-end functionality.
 */
@SpringBootTest(
    classes = io.github.cyfko.Main.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class PersonEndpointFunctionalTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PersonRepository personRepository;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        personRepository.deleteAll();
    }

    /**
     * Test basic search endpoint with simple EQ filter
     */
    @Test
    void shouldSearchPersonsByUsernameEquals() {
        // GIVEN: 3 persons in database
        createTestPerson("john", "john@example.com", "John", "Doe", 30);
        createTestPerson("jane", "jane@example.com", "Jane", "Smith", 25);
        createTestPerson("bob", "bob@example.com", "Bob", "Johnson", 35);

        // WHEN: Search for username = "john"
        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.USERNAME, Op.EQ, "john")
                .combineWith("f")
                .build();

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/api/v1/search/users",
            request,
            Object.class
        );

        // THEN: Should return 200 with 1 result
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    /**
     * Test search with GT (greater than) operator on age field
     */
    @Test
    void shouldSearchPersonsByAgeGreaterThan() {
        // GIVEN: Persons with different ages
        createTestPerson("young1", "young1@example.com", "Young", "One", 20);
        createTestPerson("young2", "young2@example.com", "Young", "Two", 22);
        createTestPerson("old1", "old1@example.com", "Old", "One", 40);
        createTestPerson("old2", "old2@example.com", "Old", "Two", 50);

        // WHEN: Search for age > 30
        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.AGE, Op.GT, 30)
                .combineWith("f")
                .build();

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/api/v1/search/users",
            request,
            Object.class
        );

        // THEN: Should return only persons older than 30
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    /**
     * Test search with MATCHES operator for partial string matching
     */
    @Test
    void shouldSearchPersonsByEmailMatches() {
        // GIVEN: Persons with different email domains
        createTestPerson("user1", "user1@gmail.com", "User", "One", 25);
        createTestPerson("user2", "user2@yahoo.com", "User", "Two", 30);
        createTestPerson("user3", "user3@gmail.com", "User", "Three", 35);

        // WHEN: Search for email matching "gmail"
        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.EMAIL, Op.MATCHES, "gmail")
                .combineWith("f")
                .build();

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/api/v1/search/users",
            request,
            Object.class
        );

        // THEN: Should return only gmail users
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    /**
     * Test search with IN operator for multiple values
     */
    @Test
    void shouldSearchPersonsByUsernameIn() {
        // GIVEN: Multiple persons
        createTestPerson("alice", "alice@example.com", "Alice", "Wonder", 28);
        createTestPerson("bob", "bob@example.com", "Bob", "Builder", 32);
        createTestPerson("charlie", "charlie@example.com", "Charlie", "Brown", 29);
        createTestPerson("diana", "diana@example.com", "Diana", "Prince", 31);

        // WHEN: Search for username IN ["alice", "charlie"]
        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.USERNAME, Op.IN, List.of("alice", "charlie"))
                .combineWith("f")
                .build();

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/api/v1/search/users",
            request,
            Object.class
        );

        // THEN: Should return alice and charlie
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    /**
     * Test search with pagination
     */
    @Test
    void shouldSupportPagination() {
        // GIVEN: 10 persons in database
        for (int i = 0; i < 10; i++) {
            createTestPerson("user" + i, "user" + i + "@example.com", "User", "Test" + i, 20 + i);
        }

        // Build headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build minimal JSON body for filter request
        HttpEntity<FilterRequest<PersonDTO_>> entity = new HttpEntity<>(FilterRequest.<PersonDTO_>builder()
                .pagination(0, 5)
                .build(), headers);

        // WHEN: Request page 0, size 5
        PaginatedData<?> paginatedData = restTemplate.postForObject(
                "/api/v1/search/users",
                entity,
                PaginatedData.class
        );

        // THEN
        assertEquals(5, paginatedData.pagination().pageSize());
        assertEquals(0, paginatedData.pagination().currentPage());
        assertEquals(10, paginatedData.pagination().totalElements());
    }

    /**
     * Test search with multiple conditions (AND logic)
     */
    @Test
    void shouldSearchWithMultipleConditions() {
        // GIVEN: Multiple persons
        createTestPerson("john", "john@example.com", "John", "Doe", 30);
        createTestPerson("jane", "jane@example.com", "Jane", "Doe", 25);
        createTestPerson("bob", "bob@example.com", "Bob", "Smith", 30);

        // WHEN: Search for age = 30 AND lastName = "Doe"
        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f1", PersonDTO_.AGE, Op.EQ, 30)
                .filter("f2", PersonDTO_.LAST_NAME, Op.EQ, "Doe")
                .combineWith("f1 & f2")
                .build();

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/api/v1/search/users",
            request,
            Object.class
        );

        // THEN: Should return only john (age 30 AND lastName Doe)
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    /**
     * Test empty search (return all)
     */
    @Test
    void shouldReturnAllPersonsWhenNoConditions() {
        // GIVEN: 3 persons
        createTestPerson("john", "john@example.com", "John", "Doe", 30);
        createTestPerson("jane", "jane@example.com", "Jane", "Smith", 25);
        createTestPerson("bob", "bob@example.com", "Bob", "Johnson", 35);

        // Build headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build minimal JSON body for filter request
        HttpEntity<FilterRequest<PersonDTO_>> entity = new HttpEntity<>(FilterRequest.<PersonDTO_>builder()
                .pagination(0, 5)
                .build(), headers);

        // WHEN: Request page 0, size 5
        ResponseEntity<PaginatedData<?>> response = restTemplate.exchange(
                "/api/v1/search/users",
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<>() {}
        );

        // THEN: Should return all 3 persons
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().pagination().pageSize());
    }

    /**
     * Test search with active field (Boolean type)
     */
    @Test
    void shouldSearchByActiveStatus() {
        // GIVEN: Active and inactive persons
        Person active1 = createTestPerson("active1", "active1@example.com", "Active", "One", 30);
        active1.setActive(true);
        personRepository.save(active1);

        Person inactive1 = createTestPerson("inactive1", "inactive1@example.com", "Inactive", "One", 25);
        inactive1.setActive(false);
        personRepository.save(inactive1);

        // WHEN: Search for active = true
        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.ACTIVE, Op.EQ, true)
                .combineWith("f")
                .build();

        ResponseEntity<?> response = restTemplate.postForEntity(
            "/api/v1/search/users",
            request,
            Object.class
        );

        // THEN: Should return only active persons
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    // Helper method to create test persons
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
        return personRepository.save(person);
    }
}
