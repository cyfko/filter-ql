---
sidebar_position: 3
---

# Spring Adapter Reference

Complete reference documentation for the `filterql-spring` module (version 4.0.0).

---

## Maven Coordinates

```xml
<!-- Option 1: Starter (recommended) -->
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Option 2: Module only -->
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-spring</artifactId>
    <version>4.0.0</version>
</dependency>

<!-- Required external dependency -->
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>projection-metamodel-processor</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## Package Structure

```
io.github.cyfko.filterql.spring
├── Exposure                    # REST exposure annotation
├── ExposedAs                   # Field customization annotation
├── processor/
│   ├── ExposureAnnotationProcessor  # Annotation processor
│   ├── PropertyRefEnumGenerator     # Enum generator
│   ├── FilterContextGenerator       # Configuration generator
│   └── FilterControllerGenerator    # Controller generator
├── service/
│   ├── FilterQlService              # Service interface
│   └── impl/
│       └── FilterQlServiceImpl      # Implementation
├── pagination/
│   ├── PaginatedData                # Results wrapper
│   ├── PaginationInfo               # Pagination metadata
│   └── ResultMapper                 # Transformation interface
├── support/
│   ├── FilterContextRegistry        # Context registry
│   └── SpringProviderResolver       # Spring IoC resolver
└── autoconfigure/
    └── FilterQlAutoConfiguration    # Spring Boot auto-configuration
```

---

## Annotations

### @Exposure

Marks a projection class for REST controller generation.

```java
package io.github.cyfko.filterql.spring;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Exposure {
    
    /**
     * REST resource name (kebab-case).
     * Default: class name in kebab-case.
     */
    String value() default "";
    
    /**
     * Optional URI path prefix.
     */
    String basePath() default "";
    
    /**
     * Method reference for endpoint annotations.
     */
    MethodReference annotationsFrom() default @MethodReference();
}
```

#### Usage

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.filterql.spring.Exposure;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v1")
public class UserDTO {
    private Long id;
    private String username;
    private String email;
}
```

Generates an endpoint: `POST /api/v1/users/search`

#### Endpoint Annotations

To apply annotations (security, cache, etc.) to generated endpoints:

```java
@Projection(from = User.class)
@Exposure(value = "users")
public class UserDTO {
    
    // Template method for annotations
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable("userCache")
    private static void exposureEndpoint() {}
}
```

Or with a shared template class:

```java
// Shared templates
public class SecurityTemplates {
    
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable("adminCache")
    @RateLimiter(name = "admin")
    public static void adminEndpoint() {}
    
    @PreAuthorize("hasRole('USER')")
    @RateLimiter(name = "user")
    public static void userEndpoint() {}
}

// Usage
@Projection(from = User.class)
@Exposure(
    value = "users",
    annotationsFrom = @MethodReference(
        type = SecurityTemplates.class,
        method = "adminEndpoint"
    )
)
public class UserDTO { }
```

---

### @ExposedAs

Customizes a field's exposure in the generated PropertyRef enum.

```java
package io.github.cyfko.filterql.spring;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface ExposedAs {
    
    /**
     * Symbolic name in the generated enum.
     */
    String value();
    
    /**
     * Supported operators.
     */
    Op[] operators() default {};
    
    /**
     * Whether the field is exposed to filtering.
     */
    boolean exposed() default true;
}
```

#### Usage

```java
@Projection(from = User.class)
@Exposure(value = "users")
public class UserDTO {
    
    private Long id;
    
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String username;
    
    @ExposedAs(value = "USER_EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String email;
    
    @ExposedAs(exposed = false)  // Exclude from filtering
    private String internalField;
}
```

Generates:

```java
public enum UserDTO_ implements PropertyReference {
    USERNAME,
    USER_EMAIL;
    // internalField not included
}
```

#### Virtual Fields

Virtual fields allow custom predicate logic without a corresponding entity field:

```java
import io.github.cyfko.filterql.core.spi.PredicateResolver;

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
```

**Method requirements:**
- Must be `public static` (or instance method if managed by Spring)
- Return type: `PredicateResolver<E>` where `E` is the entity type
- Parameters: `(String op, Object[] args)` — the operator and filter arguments

Virtual fields can also be non-static (instance methods) when they need to access Spring beans.

---

## Services

### FilterQlService

Main interface for filter execution.

```java
package io.github.cyfko.filterql.spring.service;

public interface FilterQlService {
    
    /**
     * Search with Map result.
     * 
     * @param refClass PropertyReference enum class
     * @param filterRequest filter request
     * @return paginated data
     */
    <P extends Enum<P> & PropertyReference> 
    PaginatedData<Map<String, Object>> search(
        Class<P> refClass, 
        FilterRequest<P> filterRequest
    );
    
    /**
     * Search with custom mapping.
     * 
     * @param projectionClass target DTO class
     * @param filterRequest filter request
     * @param resultMapper result mapper
     * @return typed paginated data
     */
    <R, P extends Enum<P> & PropertyReference> 
    PaginatedData<R> search(
        Class<R> projectionClass, 
        FilterRequest<P> filterRequest, 
        ResultMapper<R> resultMapper
    );
}
```

#### Injection and Usage

```java
import io.github.cyfko.filterql.spring.service.FilterQlService;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    private final FilterQlService filterQlService;
    
    public UserService(FilterQlService filterQlService) {
        this.filterQlService = filterQlService;
    }
    
    public PaginatedData<Map<String, Object>> searchUsers(
            FilterRequest<UserDTO_> request) {
        return filterQlService.search(UserDTO_.class, request);
    }
}
```

---

### FilterQlServiceImpl

Default implementation of `FilterQlService`.

```java
package io.github.cyfko.filterql.spring.service.impl;

@Service
public class FilterQlServiceImpl implements FilterQlService {
    
    @PersistenceContext
    private EntityManager em;
    
    private final FilterContextRegistry contextRegistry;
    private final InstanceResolver instanceResolver;
    
    // Implementation...
}
```

---

## Pagination

### PaginatedData

Immutable wrapper for paginated results.

```java
package io.github.cyfko.filterql.spring.pagination;

public record PaginatedData<T>(
    List<T> data,
    PaginationInfo pagination
) {
    
    /**
     * Constructor with defensive copy.
     */
    public PaginatedData(List<T> data, PaginationInfo pagination) {
        this.data = List.copyOf(data);
        this.pagination = pagination;
    }
    
    /**
     * Constructor from Spring Data Page.
     */
    public PaginatedData(Page<T> page) {
        this(page.getContent(), PaginationInfo.from(page));
    }
    
    /**
     * Transforms data with a mapper.
     * 
     * @param mapper transformation function
     * @return new paginated data
     */
    public <R> PaginatedData<R> map(Function<T, R> mapper) {
        return new PaginatedData<>(
            data.stream().map(mapper).collect(Collectors.toList()), 
            pagination
        );
    }
}
```

---

### PaginationInfo

Pagination metadata.

```java
package io.github.cyfko.filterql.spring.pagination;

public record PaginationInfo(
    int currentPage,
    int pageSize,
    long totalElements
) {
    
    /**
     * Calculates the total number of pages.
     */
    public int totalPages() {
        return (int) Math.ceil((double) totalElements / pageSize);
    }
    
    /**
     * Creates from a Spring Data Page.
     */
    public static PaginationInfo from(Page<?> page) {
        return new PaginationInfo(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
        );
    }
}
```

---

### ResultMapper

Result transformation interface.

```java
package io.github.cyfko.filterql.spring.pagination;

@FunctionalInterface
public interface ResultMapper<R> {
    
    /**
     * Transforms a Map result to target type.
     * 
     * @param row raw data
     * @return transformed object
     */
    R map(Map<String, Object> row);
}
```

#### Usage Example

```java
ResultMapper<UserDTO> mapper = row -> new UserDTO(
    (Long) row.get("id"),
    (String) row.get("username"),
    (String) row.get("email")
);

PaginatedData<UserDTO> result = filterQlService.search(
    UserDTO.class, 
    request, 
    mapper
);
```

---

## Registry

### FilterContextRegistry

Central registry for all `JpaFilterContext` beans.

```java
package io.github.cyfko.filterql.spring.support;

@Component
public class FilterContextRegistry {
    
    /**
     * Builds the registry from injected contexts.
     */
    public FilterContextRegistry(List<JpaFilterContext<?>> contexts);
    
    /**
     * Retrieves the context for an enum class.
     * 
     * @param enumClass PropertyReference enum class
     * @return corresponding context
     * @throws IllegalArgumentException if not found
     */
    public <P extends Enum<P> & PropertyReference> 
    JpaFilterContext<?> getContext(Class<P> enumClass);
}
```

---

### SpringProviderResolver

Spring bean resolver for `@Computed` fields.

```java
package io.github.cyfko.filterql.spring.service.impl;

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

---

## Annotation Processor

### ExposureAnnotationProcessor

Compilation processor that generates FilterQL code.

```java
package io.github.cyfko.filterql.spring.processor;

@SupportedAnnotationTypes("io.github.cyfko.projection.Projection")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class ExposureAnnotationProcessor extends AbstractProcessor {
    
    /**
     * Processes @Projection annotated classes.
     * Generates:
     * - PropertyRef enum ({ClassName}_)
     * - FilterContext configuration (FilterQlContextConfig)
     * - REST Controller (FilterQlController) if @Exposure is present
     */
    @Override
    public boolean process(
        Set<? extends TypeElement> annotations, 
        RoundEnvironment roundEnv
    );
}
```

### Generated Artifacts

For a `UserDTO` class annotated with `@Projection` and `@Exposure`:

#### 1. PropertyRef Enum

```java
// Generated file: UserDTO_.java
public enum UserDTO_ implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE;
    
    @Override
    public Class<?> getType() {
        var pm = ProjectionRegistry.getMetadataFor(UserDTO.class);
        return switch(this) {
            case USERNAME -> pm.getDirectMapping("username", true).get().dtoFieldType();
            case EMAIL -> pm.getDirectMapping("email", true).get().dtoFieldType();
            case AGE -> pm.getDirectMapping("age", true).get().dtoFieldType();
        };
    }
    
    @Override
    public Set<Op> getSupportedOperators() {
        return switch(this) {
            case USERNAME -> Set.of(Op.EQ, Op.MATCHES, Op.IN);
            case EMAIL -> Set.of(Op.EQ, Op.MATCHES);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE);
        };
    }
    
    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

#### 2. FilterContext Configuration

```java
// Generated file: FilterQlContextConfig.java
@Configuration
public class FilterQlContextConfig {
    
    @Bean
    public JpaFilterContext<?> userDTOContext(InstanceResolver instanceResolver) {
        return new JpaFilterContext<>(UserDTO_.class, (ref) -> switch (ref) {
            case USERNAME -> "username";
            case EMAIL -> "email";
            case AGE -> "age";
        });
    }
}
```

#### 3. REST Controller

```java
// Generated file: FilterQlController.java
@RestController
public class FilterQlController {
    
    @Autowired
    private FilterQlService filterQlService;
    
    @PostMapping("/api/v1/users/search")
    public ResponseEntity<PaginatedData<Map<String, Object>>> searchUserDTO(
        @RequestBody @Validated FilterRequest<UserDTO_> request
    ) {
        return ResponseEntity.ok(filterQlService.search(UserDTO_.class, request));
    }
}
```

---

## Auto-Configuration

### FilterQlAutoConfiguration

Spring Boot automatic configuration.

```java
package io.github.cyfko.filterql.spring.autoconfigure;

@Configuration
@ConditionalOnClass(JpaFilterContext.class)
@EnableConfigurationProperties(FilterQlProperties.class)
public class FilterQlAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public FilterContextRegistry filterContextRegistry(
            List<JpaFilterContext<?>> contexts) {
        return new FilterContextRegistry(contexts);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public InstanceResolver instanceResolver(ApplicationContext ctx) {
        return new SpringProviderResolver(ctx);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public FilterQlService filterQlService(
            EntityManager em,
            FilterContextRegistry registry,
            InstanceResolver resolver) {
        return new FilterQlServiceImpl(em, registry, resolver);
    }
}
```

---

## Complete Example

### DTO with Projection and Exposure

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Projected;
import io.github.cyfko.projection.Computed;
import io.github.cyfko.filterql.spring.Exposure;
import io.github.cyfko.filterql.spring.ExposedAs;
import org.springframework.security.access.prepost.PreAuthorize;

@Projection(from = User.class)
@Exposure(value = "users", basePath = "/api/v1")
public class UserDTO {
    
    private Long id;
    
    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String username;
    
    @Projected(from = "email")
    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String userEmail;
    
    @Projected(from = "address.city.name")
    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.IN})
    private String cityName;
    
    @Computed(provider = AgeCalculator.class, method = "calculateAge")
    @ExposedAs(exposed = false)  // Not filterable
    private Integer age;
    
    @Projected(from = "orders")
    private List<OrderSummaryDTO> orders;
    
    // Annotation template for the generated endpoint
    @PreAuthorize("hasRole('USER')")
    private static void exposureEndpoint() {}
}
```

### HTTP Request

```bash
curl -X POST http://localhost:8080/api/v1/users/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "filters": {
      "nameFilter": {"ref": "USERNAME", "op": "MATCHES", "value": "%john%"},
      "cityFilter": {"ref": "CITY", "op": "IN", "value": ["Paris", "Lyon"]}
    },
    "combineWith": "nameFilter & cityFilter",
    "projection": ["id", "username", "userEmail", "cityName", "orders[size=5].id,total"],
    "pagination": {"page": 1, "size": 20}
  }'
```

### Response

```json
{
  "data": [
    {
      "id": 1,
      "username": "john.doe",
      "userEmail": "john@example.com",
      "cityName": "Paris",
      "orders": [
        {"id": 101, "total": 150.00},
        {"id": 98, "total": 75.50}
      ]
    }
  ],
  "pagination": {
    "currentPage": 1,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## Next Steps

- [Protocol Specification](../protocol) - Formal message format
- [Projection Guide](../guides/projection) - Advanced projection syntax
