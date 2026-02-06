package io.github.cyfko.filterql.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.cyfko.*;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for virtual field resolver execution.
 * Verifies that virtual field resolvers generate correct predicates and work in queries.
 */
@SpringBootTest(classes = io.github.cyfko.Main.class)
@ActiveProfiles("test")
class VirtualFieldResolverTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private FilterQlService filterQlService;

    @Autowired
    private UserTenancyService userTenancyService;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();
    }

    @Test
    void shouldFilterByFullNameVirtualField() {
        createTestPerson("john.doe", "john@example.com", "John", "Doe", 30);
        createTestPerson("jane.smith", "jane@example.com", "Jane", "Smith", 25);
        createTestPerson("john.smith", "johnsmith@example.com", "John", "Smith", 35);

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", new FilterDefinition<>(PersonDTO_.FULL_NAME, Op.MATCHES, "John"))
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));
        assertEquals(2, result.pagination().totalElements(), "Should find 2 persons with 'John' in full name");
    }

    @Test
    void shouldFilterByIsAdminVirtualField() {
        createTestPerson("admin.user", "admin@example.com", "Admin", "User", 35);
        createTestPerson("regular.user", "regular@example.com", "Regular", "User", 28);
        createTestPerson("superadmin", "superadmin@example.com", "Super", "Admin", 40);

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.IS_ADMIN, Op.EQ, true)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));
        assertNotNull(result, "IS_ADMIN query should execute");
    }

    @Test
    void shouldFilterByHasOrgVirtualField() {
        createTestPerson("user1", "user1@example.com", "User", "One", 30);
        createTestPerson("user2", "user2@example.com", "User", "Two", 25);

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.HAS_ORG, Op.EQ, true)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));
        assertNotNull(result, "HAS_ORG query should execute");
    }

    @Test
    void shouldCombineRegularAndVirtualFieldFilters() {
        createTestPerson("john.admin", "johnadmin@example.com", "John", "Admin", 35);
        createTestPerson("jane.user", "janeuser@example.com", "Jane", "User", 25);
        createTestPerson("bob.admin", "bobadmin@example.com", "Bob", "Admin", 40);

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("age", PersonDTO_.AGE, Op.GT, 30)
                .filter("name", PersonDTO_.FULL_NAME, Op.MATCHES, "Admin")
                .combineWith("age & name")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));
        assertNotNull(result, "Combined query should execute");
    }

    @Test
    void shouldSupportPaginationWithVirtualFields() {
        for (int i = 0; i < 10; i++) {
            createTestPerson("user" + i, "user" + i + "@example.com", "First" + i, "Last" + i, 20 + i);
        }

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.FULL_NAME, Op.MATCHES, "First")
                .combineWith("f")
                .pagination(0, 5)
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));
        assertNotNull(result);
        assertTrue(result.data().size() <= 5);
    }

    @Test
    void shouldSupportMultipleVirtualFieldConditions() {
        createTestPerson("admin.john", "adminjohn@example.com", "John", "Admin", 35);
        createTestPerson("user.jane", "userjane@example.com", "Jane", "User", 25);

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("isAdmin", PersonDTO_.IS_ADMIN, Op.EQ, true)
                .filter("name", PersonDTO_.FULL_NAME, Op.MATCHES, "John")
                .combineWith("isAdmin & name")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));
        assertNotNull(result);
    }

    @Test
    void shouldSupportBothStaticAndInstanceResolvers() {
        createTestPerson("user1", "user1@example.com", "Test", "User", 30);

        FilterRequest<PersonDTO_> staticRequest = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.FULL_NAME, Op.MATCHES, "Test")
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> staticResult = filterQlService.search(PersonDTO.class, staticRequest, v -> mapper.convertValue(v, PersonDTO.class));

        FilterRequest<PersonDTO_> instanceRequest = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.HAS_ORG, Op.EQ, true)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> instanceResult = filterQlService.search(PersonDTO.class, instanceRequest, v -> mapper.convertValue(v, PersonDTO.class));

        assertNotNull(staticResult, "Static resolver should work");
        assertNotNull(instanceResult, "Instance resolver should work");
    }

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