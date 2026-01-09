package io.github.cyfko.filterql.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.cyfko.Person;
import io.github.cyfko.PersonDTO;
import io.github.cyfko.PersonRepository;
import io.github.cyfko.PersonDTO_;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.pagination.PaginatedData;
import io.github.cyfko.filterql.spring.service.FilterQlService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JPA query execution tests for Person entity filtering.
 * Tests actual database queries generated from FilterRequest.
 */
@SpringBootTest
@AutoConfigureTestEntityManager
@ActiveProfiles("test")
@Transactional
class PersonQueryExecutionTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private FilterQlService filterQlService;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        personRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void shouldFilterByUsernameEquals() {
        createAndPersistPerson("john", "john@example.com", "John", "Doe", 30);
        createAndPersistPerson("jane", "jane@example.com", "Jane", "Smith", 25);
        createAndPersistPerson("bob", "bob@example.com", "Bob", "Johnson", 35);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.USERNAME, Op.EQ, "john")
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(1, result.pagination().totalElements());
        assertEquals("john", result.data().get(0).getUsername());
    }

    @Test
    void shouldFilterByUsernameNotEquals() {
        createAndPersistPerson("john", "john@example.com", "John", "Doe", 30);
        createAndPersistPerson("jane", "jane@example.com", "Jane", "Smith", 25);
        createAndPersistPerson("bob", "bob@example.com", "Bob", "Johnson", 35);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.USERNAME, Op.NE, "john")
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().noneMatch(p -> p.getUsername().equals("john")));
    }

    @Test
    void shouldFilterByAgeGreaterThan() {
        createAndPersistPerson("young", "young@example.com", "Young", "Person", 20);
        createAndPersistPerson("middle", "middle@example.com", "Middle", "Person", 30);
        createAndPersistPerson("old", "old@example.com", "Old", "Person", 40);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.AGE, Op.GT, 25)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(p -> p.getAge() > 25));
    }

    @Test
    void shouldFilterByAgeLessThan() {
        createAndPersistPerson("young", "young@example.com", "Young", "Person", 20);
        createAndPersistPerson("middle", "middle@example.com", "Middle", "Person", 30);
        createAndPersistPerson("old", "old@example.com", "Old", "Person", 40);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.AGE, Op.LT, 35)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(p -> p.getAge() < 35));
    }

    @Test
    void shouldFilterByAgeGreaterThanOrEqual() {
        createAndPersistPerson("p1", "p1@example.com", "P", "One", 20);
        createAndPersistPerson("p2", "p2@example.com", "P", "Two", 30);
        createAndPersistPerson("p3", "p3@example.com", "P", "Three", 40);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.AGE, Op.GTE, 30)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(p -> p.getAge() >= 30));
    }

    @Test
    void shouldFilterByAgeLessThanOrEqual() {
        createAndPersistPerson("p1", "p1@example.com", "P", "One", 20);
        createAndPersistPerson("p2", "p2@example.com", "P", "Two", 30);
        createAndPersistPerson("p3", "p3@example.com", "P", "Three", 40);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.AGE, Op.LTE, 30)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(p -> p.getAge() <= 30));
    }

    @Test
    void shouldFilterByAgeRange() {
        createAndPersistPerson("p1", "p1@example.com", "P", "One", 20);
        createAndPersistPerson("p2", "p2@example.com", "P", "Two", 30);
        createAndPersistPerson("p3", "p3@example.com", "P", "Three", 40);
        createAndPersistPerson("p4", "p4@example.com", "P", "Four", 50);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.AGE, Op.RANGE, List.of(25, 45))
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(p -> p.getAge() >= 25 && p.getAge() <= 45));
    }

    @Test
    void shouldFilterByEmailMatches() {
        createAndPersistPerson("user1", "user1@gmail.com", "User", "One", 25);
        createAndPersistPerson("user2", "user2@yahoo.com", "User", "Two", 30);
        createAndPersistPerson("user3", "user3@gmail.com", "User", "Three", 35);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.EMAIL, Op.MATCHES, "%gmail%")
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(p -> p.getEmail().contains("gmail")));
    }

    @Test
    void shouldFilterByUsernameIn() {
        createAndPersistPerson("alice", "alice@example.com", "Alice", "Wonder", 28);
        createAndPersistPerson("bob", "bob@example.com", "Bob", "Builder", 32);
        createAndPersistPerson("charlie", "charlie@example.com", "Charlie", "Brown", 29);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.USERNAME, Op.IN, List.of("alice", "charlie"))
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(2, result.pagination().totalElements());
        assertTrue(result.data().stream().allMatch(
                p -> p.getUsername().equals("alice") || p.getUsername().equals("charlie")
        ));
    }

    @Test
    void shouldFilterWithMultipleConditionsAnd() {
        createAndPersistPerson("john", "john@example.com", "John", "Doe", 30);
        createAndPersistPerson("jane", "jane@example.com", "Jane", "Doe", 25);
        createAndPersistPerson("bob", "bob@example.com", "Bob", "Smith", 30);
        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("a", PersonDTO_.AGE, Op.EQ, 30)
                .filter("b", PersonDTO_.LAST_NAME, Op.EQ, "Doe")
                .combineWith("a & b")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(1, result.pagination().totalElements());
        assertEquals("john", result.data().get(0).getUsername());
    }

    @Test
    void shouldFilterByActiveStatus() {
        Person active = createAndPersistPerson("active", "active@example.com", "Active", "User", 30);
        active.setActive(true);
        entityManager.persist(active);

        Person inactive = createAndPersistPerson("inactive", "inactive@example.com", "Inactive", "User", 25);
        inactive.setActive(false);
        entityManager.persist(inactive);

        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.ACTIVE, Op.EQ, true)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(1, result.pagination().totalElements());
        assertTrue(result.data().get(0).getActive());
    }

    @Test
    void shouldFilterByRegisteredAtAfter() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime tomorrow = now.plusDays(1);

        Person p1 = createAndPersistPerson("p1", "p1@example.com", "P", "One", 30);
        p1.setRegisteredAt(yesterday);
        entityManager.persist(p1);

        Person p2 = createAndPersistPerson("p2", "p2@example.com", "P", "Two", 25);
        p2.setRegisteredAt(tomorrow);
        entityManager.persist(p2);

        entityManager.flush();

        FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
                .filter("f", PersonDTO_.REGISTERED_AT, Op.GT, now)
                .combineWith("f")
                .build();

        PaginatedData<PersonDTO> result = filterQlService.search(PersonDTO.class, request, v -> mapper.convertValue(v, PersonDTO.class));

        assertEquals(1, result.pagination().totalElements());
        assertEquals("p2", result.data().get(0).getUsername());
    }

//    @Test
//    void shouldSupportPagination() {
//        for (int i = 0; i < 10; i++) {
//            createAndPersistPerson("user" + i, "user" + i + "@example.com", "User", "Test" + i, 20 + i);
//        }
//        entityManager.flush();
//
//        Page<Person> result = filterQlService.search(Person.class, null, PageRequest.of(1, 3), personRepository);
//
//        assertEquals(10, result.pagination().totalElements());
//        assertEquals(4, result.getTotalPages());
//        assertEquals(3, result.data().size());
//        assertEquals(1, result.getNumber());
//    }

    private Person createAndPersistPerson(String username, String email, String firstName, String lastName, Integer age) {
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(email);
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setAge(age);
        person.setActive(true);
        person.setRegisteredAt(LocalDateTime.now());
        person.setBirthDate(LocalDate.now().minusYears(age));
        return entityManager.persist(person);
    }
}