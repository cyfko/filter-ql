# FilterQL Spring API

[![Maven Central](https://img.shields.io/maven-central/v/io.github.cyfko/filterql-spring-api)](https://search.maven.org/artifact/io.github.cyfko/filterql-spring-api)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3%2B-brightgreen?logo=spring-boot)](https://spring.io/projects/spring-boot)

**FilterQL Spring API** provides runtime components, annotations, and service layer abstractions for integrating FilterQL with Spring Boot applications.

## 🎯 Goals

This module provides:

- **Annotations**: `@Exposure` and `@ExposedAs` for marking projections
- **Service Layer**: `FilterQlService` for executing filtered queries
- **Pagination Support**: `PaginatedData` and `PaginationInfo` wrappers
- **Spring Integration**: Auto-configuration and IoC support
- **Registry**: `FilterContextRegistry` for managing filter contexts

## 📋 Prerequisites

- **Java 17+**
- **Spring Boot 3.3.5+**
- **FilterQL JPA Adapter 2.0.0+**
- **Jakarta Persistence API 3.1.0+**

## 🚀 Installation

### Maven

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring</artifactId>
    <version>4.0.0</version>
</dependency>
```

> **Note:** To enable code generation (PropertyRef enums, controllers), also add `filterql-spring-processor` as a processor.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>${maven-compiler-plugin.version}</version>
            <configuration>
                <source>${java.version}</source>
                <target>${java.version}</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.cyfko</groupId>
                        <artifactId>filterql-spring-processor</artifactId>
                        <version>${filterql-spring-processor.version}</version>
                    </path>
                </annotationProcessorPaths>
                <!-- Show detailed output for debugging -->
                <showWarnings>true</showWarnings>
                <compilerArgs>
                    <arg>-Xlint:unchecked</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## 📖 Core Components

### 1. Annotations

#### @Exposure

Marks a projection class for REST controller generation.
This annotation triggers the generation of a Spring `@RestController` that handles search requests.

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Exposure {
    /**
     * REST resource name (kebab-case).
     * <p>
     * Usage determines the endpoint URL: {@code [base-path]/[value]/search}
     * Defaults to the kebab-case form of the entity class name if empty.
     * </p>
     */
    String value() default "";

    /**
     * Optional URI path prefix for the REST endpoints.
     * <p>
     * Default depends on global configuration (often "/api").
     * </p>
     */
    String basePath() default "";

    /**
     * Strategy determining the endpoint return type and behavior.
     * <p>
     * Available strategies:
     * <ul>
     *   <li>{@code PAGINATED} (default) - Returns {@code PaginatedData<T>}</li>
     *   <li>{@code LIST} - Returns {@code List<T>}</li>
     *   <li>{@code CUSTOM} - Requires a manual handler method definition</li>
     * </ul>
     * </p>
     */
    Strategy strategy() default Strategy.PAGINATED;

    /**
     * Pipeline of filter transformations applied before the handler execution.
     * <p>
     * Pipes are static methods that intercept and modify the {@link io.github.cyfko.filterql.core.model.FilterRequest}
     * before it reaches the query execution phase. They are useful for:
     * <ul>
     *     <li>Tenant isolation (forcing tenantId filter)</li>
     *     <li>Security constraints (filtering by ownership)</li>
     *     <li>Input sanitization</li>
     * </ul>
     * Execution order matches the array order.
     * </p>
     */
    Method[] pipes() default {};

    /**
     * Reference to a custom handler method.
     * <p>
     * Allows overriding the default controller logic entirely.
     * The referenced method must match the signature expected by the chosen {@code strategy}.
     * </p>
     */
    Method handler() default @Method();

    /**
     * Custom name for the generated endpoint method.
     * Defaults to "search[EntityName]".
     */
    String endpointName() default "";
}
```

**Usage:**

```java
import io.github.cyfko.filterql.spring.annotation.Exposure;
import io.github.cyfko.projection.Method;

@Projection(from = User.class)
@Exposure(
    value = "users",
    basePath = "/api/v1",
    pipes = {
        @Method(type = SecurityFilters.class, value = "isolateTenant"),
        @Method("removeDeleted")
    }
)
public interface UserDTO {
    // ...
}
```

#### @ExposedAs

Customizes how a field is exposed in the filter request criteria or defines a virtual filter property.

This annotation has two primary use cases:

1.  **Projection Customization** (on Getter methods)
    Overrides the symbolic name and restricts allowed operators for a field that is already projected.

2.  **Virtual Field Definition** (on Provider methods)
    Defines a purely virtual filter property that translates to a complex JPA predicate (e.g., "fullName" mapping to `firstName` + `lastName`).

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface ExposedAs {
    /**
     * Symbolic name in generated enum or filter request.
     */
    String value();

    /**
     * Whitelist of supported filter operators.
     * If empty, all operators applicable to the type are allowed.
     */
    Op[] operators() default {};

    /**
     * Whether this field is exposed in the generated metamodel.
     * Default: true.
     */
    boolean exposed() default true;
}
```

**Usage Case 1: Projection Customization**

```java
@Projection(from = User.class)
public interface UserDTO {
    
    @Projected
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES})
    String getUsername();
}
```

**Usage Case 2: Virtual Field Definition**

To define a virtual field, add a static method in a registered `@Provider` class:

```java
public class UserFilters {
    
    @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
    public static PredicateResolver<User> fullNameSearch(String op, Object[] args) {
        return (root, query, cb) -> {
            String pattern = "%" + args[0] + "%";
            return cb.or(
                cb.like(root.get("firstName"), pattern),
                cb.like(root.get("lastName"), pattern)
            );
        };
    }
}
```

### 2. Service Layer

#### FilterQlService

Main service interface for executing filtered queries.

```java
public interface FilterQlService {

    /**
     * Execute filter query and return paginated results as Map.
     */
    <P extends Enum<P> & PropertyReference>
    PaginatedData<Map<String, Object>> search(
        Class<P> refClass,
        FilterRequest<P> filterRequest
    );

    /**
     * Execute filter query with custom result mapper.
     */
    <R, P extends Enum<P> & PropertyReference>
    PaginatedData<R> search(
        Class<R> projectionClass,
        FilterRequest<P> filterRequest,
        ResultMapper<R> resultMapper
    );

    /**
     * Execute filter query and return typed proxy implementations.
     * <p>
     * Uses JDK dynamic proxies to implement the projection interface,
     * backed by the raw query result maps. Recommended for interface-based DTOs.
     * </p>
     */
    <T, P extends Enum<P> & PropertyReference>
    PaginatedData<T> searchAs(
        Class<T> projectionInterface,
        FilterRequest<P> filterRequest
    );
}
```

**Usage:**

```java
@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private FilterQlService filterQlService;

    @PostMapping("/users/search")
    public ResponseEntity<PaginatedData<Map<String, Object>>> searchUsers(
        @RequestBody FilterRequest<UserDTO_> request
    ) {
        PaginatedData<Map<String, Object>> results =
            filterQlService.search(UserDTO_.class, request);
        return ResponseEntity.ok(results);
    }
}
```

#### ResultMapper

Functional interface for custom result mapping.

```java
@FunctionalInterface
public interface ResultMapper<R> {
    R map(Map<String, Object> row);
}
```

### 3. Pagination

#### PaginatedData

Immutable wrapper for paginated results.

```java
public record PaginatedData<T>(
    List<T> data,
    PaginationInfo pagination
) {
    // ...
}
```

#### PaginationInfo

Pagination metadata (page number, size, total elements).

### 4. Support Components

#### FilterContextRegistry

Central registry for managing `JpaFilterContext` beans. Automatically populated with all `@Projection` entities detected at startup.

Location: `io.github.cyfko.filterql.spring.support`

#### SpringProviderResolver

Bridge between FilterQL and Spring IOC.
Allows using Spring Beans as `@Provider` for virtual fields and custom computations.

Location: `io.github.cyfko.filterql.spring.service.impl`

```java
@Component
public class SpringProviderResolver implements InstanceResolver, ApplicationContextAware {
    // Resolves providers from Spring ApplicationContext
}
```

This configuration is automatically loaded via Spring Boot's auto-configuration mechanism.

### 6. Projection Proxy System

For interface-based DTOs, FilterQL provides a dynamic proxy system that eliminates
the need for concrete DTO implementations.

#### Core Components

- **`ProjectionProxyFactory`** — creates JDK `Proxy` instances that implement
  the projection interface, backed by a `Map<String, Object>` from the query result.
- **`ProjectionProxySerializer`** — custom Jackson serializer that only emits
  projected fields, preventing `FieldNotProjectedException` during serialization.
- **`ProjectionJacksonModule`** — Jackson module auto-registered by
  `FilterQlAutoConfiguration` that hooks up the serializer.

#### Usage

```java
// Define a projection interface (no implementation needed)
@Projection(from = User.class)
public interface UserDTO {
    Long getId();
    String getUsername();
    String getEmail();
}

// Query with searchAs — returns proxy implementations
PaginatedData<UserDTO> results = filterQlService.searchAs(UserDTO.class, request);

// Each item is a JDK Proxy implementing UserDTO.
// Calling a getter for a non-projected field throws FieldNotProjectedException.
// Jackson serialization works correctly via ProjectionJacksonModule.
```

#### Edge Cases

| Scenario | Behavior |
|----------|----------|
| Getter for projected field with `null` value | Returns `null` |
| Getter for non-projected field | Throws `FieldNotProjectedException` |
| `toString()` / `equals()` / `hashCode()` | Delegated to data map |
| Serialization without `ProjectionJacksonModule` | Jackson tries all getters → `FieldNotProjectedException` |

## 💡 Use Cases

### Programmatic Query Execution

```java
@Service
public class UserService {
    
    @Autowired
    private FilterQlService filterQlService;
    
    public PaginatedData<Map<String, Object>> findActiveUsers(int page, int size) {
        FilterRequest<UserDTO_> request = FilterRequest.<UserDTO_>builder()
            .filter("active", UserDTO_.STATUS, Op.EQ, UserStatus.ACTIVE)
            .combineWith("active")
            .pagination(new Pagination(page, size))
            .build();
        
        return filterQlService.search(UserDTO_.class, request);
    }
}
```

### Custom Result Mapping

```java
@Service
public class ReportService {
    
    @Autowired
    private FilterQlService filterQlService;
    
    public PaginatedData<UserReport> generateReport(
        FilterRequest<UserDTO_> criteria
    ) {
        return filterQlService.search(
            UserReport.class,
            criteria,
            row -> new UserReport(
                (Long) row.get("id"),
                (String) row.get("username"),
                (Integer) row.get("age"),
                calculateScore(row)
            )
        );
    }
    
    private double calculateScore(Map<String, Object> row) {
        // Custom computation
        return 0.0;
    }
}
```

### Testing

```java
@SpringBootTest
class FilterQlServiceTest {
    
    @Autowired
    private FilterQlService filterQlService;
    
    @Test
    void testSearch() {
        FilterRequest<UserDTO_> request = FilterRequest.<UserDTO_>builder()
            .filter("f1", UserDTO_.USERNAME, Op.MATCHES, "john%")
            .combineWith("f1")
            .pagination(new Pagination(0, 10))
            .build();
        
        PaginatedData<Map<String, Object>> result = 
            filterQlService.search(UserDTO_.class, request);
        
        assertThat(result.data()).isNotEmpty();
        assertThat(result.pagination().totalElements()).isGreaterThan(0);
    }
}
```

## 🔧 Advanced Usage

### Custom FilterContext

You can define custom `JpaFilterContext` beans manually:

```java
@Configuration
public class CustomFilterConfig {
    
    @Bean
    public JpaFilterContext<UserDTO_> customUserContext() {
        return new JpaFilterContext<>(
            UserDTO_.class,
            ref -> switch (ref) {
                case USERNAME -> "username";
                case EMAIL -> "email";
                case FULL_NAME -> (PredicateResolverMapping<User>) (op, args) -> 
                    (root, query, cb) -> {
                        String search = (String) args[0];
                        return cb.or(
                            cb.like(root.get("firstName"), "%" + search + "%"),
                            cb.like(root.get("lastName"), "%" + search + "%")
                        );
                    };
                default -> ref.name().toLowerCase();
            }
        );
    }
}
```

### Override Default Beans

```java
@Configuration
public class CustomFilterQlConfig {
    
    @Bean
    @Primary
    public FilterQlService customFilterQlService(
        EntityManager em,
        FilterContextRegistry registry,
        InstanceResolver resolver
    ) {
        return new CustomFilterQlServiceImpl(em, registry, resolver);
    }
}
```

## ⚠️ Notes

- This module provides **runtime components only**
- Code generation requires `filterql-spring-processor`
- Works with external `@Projection` annotation from [projection-spec](https://github.com/cyfko/projection-spec)
- Auto-configuration enabled by default in Spring Boot

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](../../../LICENSE) file for details.

## 👤 Author

**Frank KOSSI**

- Email: frank.kossi@kunrin.com
- Organization: [Kunrin SA](https://www.kunrin.com)

## 🔗 Links

- [GitHub Repository](https://github.com/cyfko/filterql)
- [Maven Central](https://search.maven.org/artifact/io.github.cyfko/filterql-spring)
- [FilterQL Core](../core/README.md)
- [FilterQL JPA Adapter](../filterql-jpa/README.md)
- [FilterQL Spring Processor](https://github.com/cyfko/filterql-spring-processor)