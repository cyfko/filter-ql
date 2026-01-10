# FilterQL Integration Tests

**Version:** 4.0.0  
**Java:** 21  
**Purpose:** End-to-end validation of FilterQL Spring Boot integration

Integration test module validating annotation processor functionality, code generation, and runtime query execution. **This module is NOT deployed to Maven Central.**

---

## Purpose

This module validates the complete integration chain:

**External Dependencies:**
- [Projection Spec](https://github.com/cyfko/projection-spec): Annotation specification (`@Projection`, `@Projected`, `@Computed`)
- [Projection Metamodel Processor](https://github.com/cyfko/jpa-metamodel-processor): Generates `PersistenceRegistry` and `ProjectionRegistry`

**FilterQL Components:**
1. **Annotation Processing:** Verifies that `ExposureAnnotationProcessor` correctly processes `@Projection` annotations
2. **Code Generation:** Validates generated PropertyRef enums, FilterContext beans, and REST controllers
3. **Query Execution:** Tests actual JPA query execution with various filter expressions
4. **Virtual Fields:** Tests computed field resolution via static method predicates
5. **Spring Integration:** Validates `FilterQlService`, `FilterContextRegistry`, and `PaginatedData`

---

## Module Structure

```
integration-test/
├── src/
│   ├── main/
│   │   ├── java/io/github/cyfko/
│   │   │   ├── Main.java                    # Spring Boot application
│   │   │   ├── Person.java                  # Entity
│   │   │   ├── PersonDTO.java               # @Projection DTO with @Exposure
│   │   │   ├── PersonRepository.java        # Spring Data JPA repository
│   │   │   ├── PersonTestRepository.java    # Test repository
│   │   │   ├── Address.java                 # Related entity
│   │   │   ├── AddressDTO.java              # @Projection DTO
│   │   │   ├── AddressRepository.java       # Spring Data JPA repository
│   │   │   ├── VirtualResolverConfig.class  # Provider for virtual fields
│   │   │   └── UserTenancyService.java      # Row-level security provider
│   │   └── resources/
│   │       └── application.properties       # H2 configuration
│   └── test/
│       └── java/io/github/cyfko/filterql/integration/
│           ├── PersonQueryExecutionTest.java        # JPA query tests
│           ├── PersonEndpointFunctionalTest.java    # REST endpoint tests
│           ├── MultiEntityIntegrationTest.java      # Multi-entity queries
│           ├── VirtualFieldResolverTest.java        # Virtual field tests
│           └── TestSecurityConfig.java              # Test security setup
└── target/
    └── generated-sources/annotations/
        └── io/github/cyfko/
            ├── PersonDTO_.java              # Generated PropertyRef enum
            ├── AddressDTO_.java             # Generated PropertyRef enum
            └── io/github/cyfko/filterql/spring/
                ├── config/FilterQlContextConfig.java    # Generated @Configuration
                └── controller/FilterQlController.java   # Generated REST controller
```

---

## Test Entities

### Person Entity

**Source:** [Person.java](src/main/java/io/github/cyfko/Person.java)

```java
@Entity
@Table(name = "persons")
public class Person {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String username;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    private String firstName;
    private String lastName;
    private Integer age;
    private Boolean active = true;
    private LocalDateTime registeredAt;
    private LocalDate birthDate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;
}
```

### PersonDTO Projection

**Source:** [PersonDTO.java](src/main/java/io/github/cyfko/PersonDTO.java)

**Key Annotations:**
- `@Projection(entity = Person.class)` - from [projection-spec](https://github.com/cyfko/projection-spec) (implemented by [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor))
- `@Exposure(value = "users", basePath = "/api/v1")` - from `filterql-spring`
- `@ExposedAs(...)` - from `filterql-spring`

```java
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
    
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.NE, Op.IN})
    private String username;
    
    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES, Op.NE})
    private String email;
    
    @ExposedAs(value = "AGE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    private Integer age;
    
    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String firstName;
    
    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES, Op.IN, Op.IS_NULL})
    private String lastName;
    
    @ExposedAs(value = "ACTIVE", operators = {Op.EQ})
    private Boolean active;
    
    @ExposedAs(value = "REGISTERED_AT", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    private LocalDateTime registeredAt;
    
    @ExposedAs(value = "BIRTH_DATE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE})
    private LocalDate birthDate;
    
    // Virtual field: static method returning PredicateResolverMapping
    @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
    public static PredicateResolverMapping<Person> fullNameMatches() {
        return (op, args) -> (root, query, cb) -> {
            String searchTerm = (String) args[0];
            String pattern = "%" + searchTerm + "%";
            Predicate firstName = cb.like(root.get("firstName"), pattern);
            Predicate lastName = cb.like(root.get("lastName"), pattern);
            return cb.or(firstName, lastName);
        };
    }
    
    // Endpoint annotations
    @PreAuthorize("hasAuthority('USER')")
    @Cacheable(value = "userSearchCache", key = "#req.hashCode()")
    public void searchEndpoint() {}
}
```

---

## Generated Artifacts

### PersonDTO_ Enum

**Location:** `target/generated-sources/annotations/io/github/cyfko/PersonDTO_.java`

```java
public enum PersonDTO_ implements PropertyReference {
    USERNAME, EMAIL, FIRST_NAME, LAST_NAME, AGE, ACTIVE, REGISTERED_AT, BIRTH_DATE, FULL_NAME;
    
    @Override
    public Class<?> getType() {
        var pm = ProjectionRegistry.getMetadataFor(PersonDTO.class);
        return switch(this) {
            case USERNAME -> pm.getDirectMapping("username", true).get().dtoFieldType();
            case EMAIL -> pm.getDirectMapping("email", true).get().dtoFieldType();
            case AGE -> pm.getDirectMapping("age", true).get().dtoFieldType();
            case FIRST_NAME -> pm.getDirectMapping("firstName", true).get().dtoFieldType();
            case LAST_NAME -> pm.getDirectMapping("lastName", true).get().dtoFieldType();
            case ACTIVE -> pm.getDirectMapping("active", true).get().dtoFieldType();
            case REGISTERED_AT -> pm.getDirectMapping("registeredAt", true).get().dtoFieldType();
            case BIRTH_DATE -> pm.getDirectMapping("birthDate", true).get().dtoFieldType();
            case FULL_NAME -> Object.class; // Virtual field
        };
    }
    
    @Override
    public Set<Op> getSupportedOperators() {
        return switch(this) {
            case USERNAME -> Set.of(Op.EQ, Op.MATCHES, Op.NE, Op.IN);
            case EMAIL -> Set.of(Op.EQ, Op.MATCHES, Op.NE);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE);
            case FIRST_NAME -> Set.of(Op.EQ, Op.MATCHES, Op.IN);
            case LAST_NAME -> Set.of(Op.EQ, Op.MATCHES, Op.IN, Op.IS_NULL);
            case ACTIVE -> Set.of(Op.EQ);
            case REGISTERED_AT -> Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE);
            case BIRTH_DATE -> Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE);
            case FULL_NAME -> Set.of(Op.MATCHES);
        };
    }
    
    @Override
    public Class<?> getEntityType() {
        return Person.class;
    }
}
```

### FilterQlContextConfig

**Location:** `target/generated-sources/annotations/io/github/cyfko/filterql/spring/config/FilterQlContextConfig.java`

```java
@Configuration
public class FilterQlContextConfig {
    
    @Bean
    public JpaFilterContext<?> personDTOContext(InstanceResolver instanceResolver) {
        return new JpaFilterContext<>(PersonDTO_.class, (ref) -> switch (ref) {
            case USERNAME -> "username";  // Simple path mapping
            case EMAIL -> "email";
            case FIRST_NAME -> "firstName";
            case LAST_NAME -> "lastName";
            case AGE -> "age";
            case FULL_NAME -> PersonDTO.fullNameMatches();  // Virtual field returns PredicateResolverMapping
            // ... other cases
        });
    }
    
    @Bean
    public JpaFilterContext<?> addressDTOContext() {
        return new JpaFilterContext<>(AddressDTO_.class, (ref) -> switch (ref) {
            // Similar mappings for AddressDTO
        });
    }
}
```

**Architecture:**
- Constructor signature: `new JpaFilterContext<>(Class<P> enumClass, Function<P, Object> mappingBuilder)`
- Mapping function returns:
  - `String` for direct JPA paths (e.g., `"username"`, `"address.city.name"`)
  - `PredicateResolverMapping<E>` for custom predicate logic (e.g., virtual fields)
- `InstanceResolver` autowired for provider bean resolution

### FilterQlController

**Location:** `target/generated-sources/annotations/io/github/cyfko/filterql/spring/controller/FilterQlController.java`

```java
@RestController
public class FilterQlController {
    
    @Autowired
    private FilterQlService filterQlService;
    
    @PostMapping("/api/v1/users/search")
    @PreAuthorize("hasAuthority('USER')")
    @Cacheable(value = "userSearchCache", key = "#req.hashCode()")
    public ResponseEntity<PaginatedData<Map<String, Object>>> searchPersonDTO(
        @RequestBody @Validated FilterRequest<PersonDTO_> request
    ) {
        return ResponseEntity.ok(filterQlService.search(PersonDTO_.class, request));
    }
}
```

---

## Test Scenarios

### 1. PersonQueryExecutionTest

**Source:** [PersonQueryExecutionTest.java](src/test/java/io/github/cyfko/filterql/integration/PersonQueryExecutionTest.java)

**Coverage:**
- Basic equality (`USERNAME = 'john'`)
- Negation (`USERNAME != 'john'`)
- IN operator (`AGE IN (25, 30, 35)`)
- RANGE operator (`AGE BETWEEN 25 AND 35`)
- MATCHES operator (`EMAIL MATCHES '%@example.com'`)
- IS_NULL operator (`LAST_NAME IS_NULL`)
- Date/time filtering (`REGISTERED_AT > '2024-01-01T00:00:00'`)
- Compound expressions (`USERNAME = 'john' AND AGE > 25`)
- OR logic (`USERNAME = 'john' OR EMAIL = 'jane@example.com'`)

**Example Test:**
```java
@Test
void shouldFilterByUsernameEquals() {
    createAndPersistPerson("john", "john@example.com", "John", "Doe", 30);
    createAndPersistPerson("jane", "jane@example.com", "Jane", "Smith", 25);
    entityManager.flush();
    
    FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
        .filter("f", PersonDTO_.USERNAME, Op.EQ, "john")
        .combineWith("f")
        .build();
    
    PaginatedData<PersonDTO> result = filterQlService.search(
        PersonDTO.class, 
        request, 
        v -> mapper.convertValue(v, PersonDTO.class)
    );
    
    assertEquals(1, result.pagination().totalElements());
    assertEquals("john", result.data().get(0).getUsername());
}
```

### 2. PersonEndpointFunctionalTest

**Source:** [PersonEndpointFunctionalTest.java](src/test/java/io/github/cyfko/filterql/integration/PersonEndpointFunctionalTest.java)

**Coverage:**
- HTTP POST to `/api/v1/users/search`
- JSON request/response validation
- HTTP status codes (200, 400, 404)
- Pagination metadata validation
- Security annotations (`@PreAuthorize`)
- Caching annotations (`@Cacheable`)

### 3. VirtualFieldResolverTest

**Source:** [VirtualFieldResolverTest.java](src/test/java/io/github/cyfko/filterql/integration/VirtualFieldResolverTest.java)

**Coverage:**
- Virtual field filtering (`FULL_NAME MATCHES 'John%'`)
- Static method predicate resolution
- PredicateResolverMapping execution

**Example Test:**
```java
@Test
void shouldFilterByVirtualFullName() {
    createAndPersistPerson("john", "john@example.com", "John", "Doe", 30);
    createAndPersistPerson("jane", "jane@example.com", "Jane", "Smith", 25);
    
    FilterRequest<PersonDTO_> request = FilterRequest.<PersonDTO_>builder()
        .filter("f", PersonDTO_.FULL_NAME, Op.MATCHES, "John%")
        .combineWith("f")
        .build();
    
    PaginatedData<PersonDTO> result = filterQlService.search(
        PersonDTO.class, 
        request, 
        v -> mapper.convertValue(v, PersonDTO.class)
    );
    
    assertEquals(1, result.pagination().totalElements());
    assertTrue(result.data().get(0).getFirstName().contains("John") 
            || result.data().get(0).getLastName().contains("John"));
}
```

---

## Running Tests

### Maven

**Run All Tests:**
```bash
mvn clean test
```

**Run Specific Test:**
```bash
mvn test -Dtest=PersonQueryExecutionTest
```

**Run With Coverage:**
```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

### IDE

1. Right-click `src/test/java` → "Run Tests"
2. Or right-click specific test class → "Run Test"

### Spring Boot Application

**Start Application:**
```bash
mvn spring-boot:run
```

**Test Generated Endpoint:**
```bash
curl -X POST http://localhost:8080/api/v1/users/search \
  -H "Content-Type: application/json" \
  -d '{
    "filters": {
      "f1": {"ref": "USERNAME", "op": "MATCHES", "value": "john%"},
      "f2": {"ref": "AGE", "op": "GT", "value": 25}
    },
    "combineWith": "f1 & f2",
    "pagination": {
      "page": 0,
      "size": 20,
      "sort": [{"field": "age", "direction": "DESC"}]
    }
  }'
```

---

## Configuration

### application.properties

**Source:** [application.properties](src/main/resources/application.properties)

```properties
# H2 In-Memory Database
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# SQL Parameter Logging
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

### Dependencies

**pom.xml:**
```xml
<dependencies>
    <!-- FilterQL Spring -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring</artifactId>
        <version>4.0.0</version>
    </dependency>
    
    <!-- FilterQL JPA Adapter -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-adapter-jpa</artifactId>
        <version>2.0.0</version>
    </dependency>
    
    <!-- External Projection Processor (implements projection-spec) -->
    <!-- See: https://github.com/cyfko/jpa-metamodel-processor -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>projection-metamodel-processor</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
        <version>3.3.4</version>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.3.4</version>
    </dependency>
    
    <!-- H2 Database -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <version>2.3.232</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Key Validation Points

### 1. Code Generation

**Verified:**
- PropertyRef enum generated with correct constants
- Type resolution via `ProjectionRegistry.getMetadataFor()`
- Operator mappings match `@ExposedAs` declarations
- Virtual fields return `Object.class` as type
- FilterContext bean registered with `@Bean` annotation

### 2. Virtual Fields

**Verified:**
- Static method `fullNameMatches()` returns `PredicateResolverMapping<Person>`
- Generated context calls static method in switch case
- Virtual field predicate receives correct parameters
- OR logic in predicate works correctly

### 3. Service Layer

**Verified:**
- `FilterQlServiceImpl` autowires `FilterContextRegistry` and `InstanceResolver`
- Service converts enum name to projection class (`PersonDTO_` → `PersonDTO`)
- MultiQueryFetchStrategy executes with correct projection class
- CountStrategy counts total matches independently
- PaginatedData wraps results with correct pagination metadata

### 4. REST Endpoints

**Verified:**
- Generated controller exposes `/api/v1/users/search`
- Endpoint accepts `FilterRequest<PersonDTO_>`
- Endpoint returns `PaginatedData<Map<String, Object>>`
- Security annotations from `searchEndpoint()` method applied to generated endpoint
- Caching annotations applied correctly

---

## Debugging Generated Code

### View Generated Sources

**Maven:**
```bash
ls target/generated-sources/annotations/io/github/cyfko/
```

**Expected Output:**
```
PersonDTO_.java
AddressDTO_.java
```

### View Generated Configuration

```bash
cat target/generated-sources/annotations/io/github/cyfko/filterql/spring/config/FilterQlContextConfig.java
```

### View Generated Controller

```bash
cat target/generated-sources/annotations/io/github/cyfko/filterql/spring/controller/FilterQlController.java
```

### Enable Annotation Processor Logging

**pom.xml:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <arg>-Averbose=true</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

---

## Known Issues

### Issue: Generated Classes Not Found

**Symptom:** IDE cannot resolve `PersonDTO_`

**Solution:**
1. Run `mvn clean compile`
2. Refresh IDE project
3. Mark `target/generated-sources/annotations` as source root

### Issue: LazyInitializationException

**Symptom:** `could not initialize proxy - no Session`

**Solution:** Use `@Transactional` on test class

### Issue: Duplicate Bean Definition

**Symptom:** Multiple `JpaFilterContext` beans with same name

**Cause:** Generated config and manual config both define same bean

**Solution:** Remove manual bean definition, rely on generated config

---

## What This Module Tests

### Tested Features

**External (projection-spec + projection-metamodel-processor):**
✅ `@Projection`, `@Projected`, `@Computed` annotation processing  
✅ `PersistenceRegistry` and `ProjectionRegistry` generation  
✅ Entity metadata extraction from JPA annotations  

**FilterQL Spring:**
✅ `@Exposure` and `@ExposedAs` annotation processing  
✅ PropertyRef enum generation with correct types and operators  
✅ FilterContext bean generation with predicate mappings  
✅ REST controller generation with custom annotations  
✅ Virtual field resolution via static method predicates  

**FilterQL JPA + Core:**
✅ FilterQlService integration with FilterContextRegistry  
✅ PaginatedData wrapping with correct pagination metadata  
✅ Query execution with various operators (EQ, MATCHES, IN, RANGE, etc.)  
✅ Compound filter expressions (AND, OR)  
✅ Sorting and pagination  

### Not Tested

❌ Production security configuration (test uses `TestSecurityConfig`)  
❌ Production database (uses H2 in-memory)  
❌ Performance under load  
❌ Concurrent request handling  
❌ Transaction rollback scenarios  
❌ Custom PredicateResolver implementations  

---

## See Also

**FilterQL Modules:**
- [FilterQL Spring Documentation](../adapters/java/filterql-spring/README.md)
- [FilterQL JPA Adapter Documentation](../adapters/java/filterql-jpa/README.md)
- [FilterQL Core Documentation](../core/java/README.md)
- [Main README](../README.md)

**External Dependencies:**
- [Projection Specification](https://github.com/cyfko/projection-spec) - Annotation specification for DTO projections
- [Projection Metamodel Processor](https://github.com/cyfko/jpa-metamodel-processor) - Annotation processor implementation
- [Maven Central: projection-metamodel-processor](https://search.maven.org/artifact/io.github.cyfko/jpa-metamodel-processor)

---

## License

Licensed under the MIT License. See [LICENSE](../LICENSE) for details.
