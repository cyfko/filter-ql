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

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Exposure {
    /**
     * REST resource name (kebab-case)
     * Example: "users" generates /api/v1/users/search
     */
    String value() default "";
    
    /**
     * URI path prefix
     * Default: "/api/v1"
     */
    String basePath() default "";
    
    /**
     * Optional method reference for endpoint annotations
     * (e.g., @PreAuthorize, @Cacheable)
     */
    MethodReference annotationsFrom() default @MethodReference();
}
```

**Usage:**
```java
import io.github.cyfko.filterql.spring.annotation.Exposure;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v1")
public class UserDTO {
    // Fields...
}
```

#### @ExposedAs

Customizes field exposure in generated PropertyRef enum.

```java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface ExposedAs {
    /**
     * Symbolic name in generated enum
     */
    String value();
    
    /**
     * Supported filter operators
     */
    Op[] operators() default {};
    
    /**
     * Whether field is exposed for filtering
     */
    boolean exposed() default true;
}
```

**Usage:**
```java
import io.github.cyfko.filterql.spring.annotation.ExposedAs;
import io.github.cyfko.filterql.core.api.Op;

@Projection(from = User.class)
@Exposure("users")
public class UserDTO {
    
    @Projected
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String username;
    
    @Projected
    @ExposedAs(value = "AGE", operators = {Op.GT, Op.LT, Op.GTE, Op.LTE})
    private Integer age;
}
```

### 2. Service Layer

#### FilterQlService

Main service interface for executing filtered queries.

```java
public interface FilterQlService {
    
    /**
     * Execute filter query and return paginated results as Map
     */
    <P extends Enum<P> & PropertyReference> 
    PaginatedData<Map<String, Object>> search(
        Class<P> refClass, 
        FilterRequest<P> filterRequest
    );
    
    /**
     * Execute filter query with custom result mapper
     */
    <R, P extends Enum<P> & PropertyReference> 
    PaginatedData<R> search(
        Class<R> projectionClass, 
        FilterRequest<P> filterRequest, 
        ResultMapper<R> resultMapper
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

**Usage:**
```java
public record UserSummary(String username, String email) {}

@PostMapping("/users/summary")
public PaginatedData<UserSummary> getUserSummaries(
    @RequestBody FilterRequest<UserDTO_> request
) {
    return filterQlService.search(
        UserSummary.class,
        request,
        row -> new UserSummary(
            (String) row.get("username"),
            (String) row.get("email")
        )
    );
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
    public PaginatedData(List<T> data, PaginationInfo pagination) {
        this.data = List.copyOf(data);
        this.pagination = pagination;
    }
    
    /**
     * Transform data with mapper function
     */
    public <R> PaginatedData<R> map(Function<T, R> mapper) {
        return new PaginatedData<>(
            data.stream().map(mapper).collect(Collectors.toList()),
            pagination
        );
    }
}
```

#### PaginationInfo

Pagination metadata.

```java
public record PaginationInfo(
    int currentPage,
    int pageSize,
    long totalElements
) {
    public int totalPages() {
        return (int) Math.ceil((double) totalElements / pageSize);
    }
    
    public static PaginationInfo from(Page<?> page) {
        return new PaginationInfo(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
        );
    }
}
```

**Response Example:**
```json
{
  "data": [
    {"id": 1, "username": "john", "email": "john@example.com"}
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 4. Support Components

#### FilterContextRegistry

Central registry for managing `JpaFilterContext` beans.

```java
@Component
public class FilterContextRegistry {
    
    private final Map<Class<?>, JpaFilterContext<?>> contextByEnum;
    
    public FilterContextRegistry(List<JpaFilterContext<?>> contexts) {
        this.contextByEnum = new HashMap<>();
        for (JpaFilterContext<?> context : contexts) {
            contextByEnum.put(context.getPropertyRefClass(), context);
        }
    }
    
    /**
     * Get FilterContext for PropertyRef enum
     */
    public <P extends Enum<P> & PropertyReference> 
    JpaFilterContext<?> getContext(Class<P> enumClass) {
        JpaFilterContext<?> context = contextByEnum.get(enumClass);
        if (context == null) {
            throw new IllegalArgumentException(
                "No JpaFilterContext found for reference " + enumClass.getName()
            );
        }
        return context;
    }
}
```

#### SpringProviderResolver

Resolves computation providers from Spring ApplicationContext.

```java
@Component
public class SpringProviderResolver implements InstanceResolver {
    
    private final ApplicationContext applicationContext;
    
    public SpringProviderResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public <T> T resolve(Class<T> providerClass) {
        return applicationContext.getBean(providerClass);
    }
}
```

**Usage:**
```java
@Service
public class UserComputations {
    
    @Autowired
    private DateTimeFormatter formatter;
    
    public String getFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
```

### 5. Auto-Configuration

#### FilterQlAutoConfiguration

Automatic Spring Boot configuration.

```java
@Configuration
@ConditionalOnClass(JpaFilterContext.class)
@EnableConfigurationProperties(FilterQlProperties.class)
public class FilterQlAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public FilterContextRegistry filterContextRegistry(
        List<JpaFilterContext<?>> contexts
    ) {
        return new FilterContextRegistry(contexts);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public FilterQlService filterQlService(
        EntityManager em,
        FilterContextRegistry contextRegistry,
        InstanceResolver instanceResolver
    ) {
        return new FilterQlServiceImpl(em, contextRegistry, instanceResolver);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public InstanceResolver instanceResolver(ApplicationContext context) {
        return new SpringProviderResolver(context);
    }
}
```

This configuration is automatically loaded via Spring Boot's auto-configuration mechanism.

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

## 📦 Module Integration

This module is designed to work with:

- **filterql-spring-processor**: Code generation (PropertyRef enums, controllers)
- **filterql-adapter-jpa**: JPA Criteria API integration
- **projection-metamodel-processor**: DTO projection metadata

**Typical dependency setup:**

```xml
<dependencies>
    <!-- Runtime API -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-api</artifactId>
        <version>4.0.0</version>
    </dependency>
    
    <!-- Compile-time processor -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-spring-processor</artifactId>
        <version>4.0.0</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- JPA adapter -->
    <dependency>
        <groupId>io.github.cyfko</groupId>
        <artifactId>filterql-adapter-jpa</artifactId>
        <version>2.0.0</version>
    </dependency>
</dependencies>
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
- [FilterQL Core](../../core/java/README.md)
- [FilterQL JPA Adapter](../filterql-jpa/README.md)
- [FilterQL Spring Processor](../filterql-spring-processor/README.md)