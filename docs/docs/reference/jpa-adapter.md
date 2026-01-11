---
sidebar_position: 2
---

# JPA Adapter Reference

Complete reference documentation for the `filterql-adapter-jpa` module (version 2.0.0).

---

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-adapter-jpa</artifactId>
    <version>2.0.0</version>
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
io.github.cyfko.filterql.jpa
├── JpaFilterContext           # Main FilterContext implementation
├── JpaCondition               # JPA condition wrapper
├── mappings/
│   ├── CustomOperatorResolver   # Centralized custom operator handler
│   ├── PredicateResolverMapping # Per-property predicate mapping
│   └── InstanceResolver        # IoC bean resolution
├── strategies/
│   ├── MultiQueryFetchStrategy      # DTO strategy with batch (RECOMMENDED)
│   ├── TypedMultiQueryFetchStrategy # Typed variant
│   ├── FullEntityFetchStrategy      # Full entity retrieval
│   └── CountStrategy                # Optimized count query
├── projection/
│   ├── ProjectionUtils         # Projection utilities
│   └── CollectionPaginationParser # Inline pagination parsing
└── utils/
    └── PathResolverUtils       # JPA path navigation
```

---

## JpaFilterContext

`FilterContext` implementation for JPA Criteria API.

### Constructors

```java
package io.github.cyfko.filterql.jpa;

public class JpaFilterContext<P extends Enum<P> & PropertyReference> 
    implements FilterContext {
    
    /**
     * Constructor with default configuration.
     * 
     * @param enumClass PropertyReference enum class
     * @param mappingBuilder property → path/resolver mapping function
     */
    public JpaFilterContext(
        Class<P> enumClass, 
        Function<P, Object> mappingBuilder
    );
    
    /**
     * Constructor with custom configuration.
     * 
     * @param enumClass PropertyReference enum class
     * @param mappingBuilder mapping function
     * @param filterConfig filtering behavior configuration
     */
    public JpaFilterContext(
        Class<P> enumClass,
        Function<P, Object> mappingBuilder,
        FilterConfig filterConfig
    );
}
```

### Mapping Function

The second parameter `mappingBuilder` is a `Function<P, Object>` that returns:

- **`String`**: Direct JPA property path (e.g., `"username"`, `"address.city"`)
- **`PredicateResolverMapping<E>`**: Custom predicate logic

```java
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        // Simple mappings (String)
        case USERNAME -> "username";
        case EMAIL -> "email";
        case AGE -> "age";
        case CITY -> "address.city.name";  // Nested path
        
        // Custom mapping (PredicateResolverMapping)
        case FULL_NAME -> (PredicateResolverMapping<User>) (op, args) -> (root, query, cb) -> {
            String search = (String) args[0];
            return cb.or(
                cb.like(cb.lower(root.get("firstName")), "%" + search.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("lastName")), "%" + search.toLowerCase() + "%")
            );
        };
    }
);
```

### Methods

```java
/**
 * Returns the property reference enum class.
 */
public Class<P> getPropertyRefClass();

/**
 * Replaces the mapping function.
 * 
 * @param mappingBuilder new mapping function
 * @return previous function
 */
public Function<P, Object> setMappingBuilder(Function<P, Object> mappingBuilder);
```

---

## PredicateResolverMapping

Interface for custom predicate logic.

```java
package io.github.cyfko.filterql.jpa.mappings;

import io.github.cyfko.filterql.core.spi.PredicateResolver;

@FunctionalInterface
public interface PredicateResolverMapping<E> extends ReferenceMapping<E> {
    
    /**
     * Resolves a PredicateResolver given the operator code and arguments.
     * 
     * @param op the filter operator to apply (e.g., "EQ", "LIKE", "SOUNDEX")
     * @param args the arguments of the filter's operator
     * @return the PredicateResolver for deferred predicate generation
     */
    PredicateResolver<E> map(String op, Object[] args);
}
```

### Usage Examples

#### Multi-Field Search

```java
case FULL_NAME -> new PredicateResolverMapping<User>() {
    @Override
    public PredicateResolver<User> map(String op, Object[] args) {
        return (root, query, cb) -> {
            String search = (String) args[0];
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("firstName")), pattern),
                cb.like(cb.lower(root.get("lastName")), pattern),
                cb.like(cb.lower(cb.concat(
                    cb.concat(root.get("firstName"), " "),
                    root.get("lastName")
                )), pattern)
            );
        };
    }
};
```

#### Age Range Calculation

```java
case AGE_RANGE -> new PredicateResolverMapping<User>() {
    @Override
    public PredicateResolver<User> map(String op, Object[] args) {
        return (root, query, cb) -> {
            List<?> range = (List<?>) args[0];
            int minAge = (int) range.get(0);
            int maxAge = (int) range.get(1);
            LocalDate now = LocalDate.now();
            LocalDate maxBirthDate = now.minusYears(minAge);
            LocalDate minBirthDate = now.minusYears(maxAge + 1);
            return cb.between(root.get("birthDate"), minBirthDate, maxBirthDate);
        };
    }
};
```

#### Subquery

```java
case HAS_ORDERS -> new PredicateResolverMapping<User>() {
    @Override
    public PredicateResolver<User> map(String op, Object[] args) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Order> orderRoot = subquery.from(Order.class);
            subquery.select(cb.count(orderRoot))
                    .where(cb.equal(orderRoot.get("user"), root));
            return cb.greaterThan(subquery, 0L);
        };
    }
};
```

---

## CustomOperatorResolver

Centralized interface for handling custom operators across all properties. Added via `withCustomOperatorResolver()` fluent method.

```java
package io.github.cyfko.filterql.jpa.mappings;

@FunctionalInterface
public interface CustomOperatorResolver<P extends Enum<P> & PropertyReference> {
    
    /**
     * Resolves a custom operator to a PredicateResolver.
     *
     * @param ref  the property reference being filtered
     * @param op   the operator code (e.g., "SOUNDEX", "GEO_WITHIN")
     * @param args the filter arguments
     * @return PredicateResolver to handle this operation, or null to delegate to default
     */
    PredicateResolver<?> resolve(P ref, String op, Object[] args);
}
```

### Resolution Flow

1. `CustomOperatorResolver.resolve()` is called first
2. If it returns a non-null `PredicateResolver`, that resolver is used
3. If it returns `null`, the default mechanism (path or `PredicateResolverMapping`) is used

### Usage

```java
JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
        UserProperty.class, 
        mappingBuilder
    ).withCustomOperatorResolver((ref, op, args) -> {
        return switch (op) {
            case "SOUNDEX" -> handleSoundex(ref, args);
            case "GEO_WITHIN" -> handleGeoWithin(ref, args);
            default -> null;  // Use default handling
        };
    });
```

:::tip When to Use
- **CustomOperatorResolver**: For operators that apply to multiple properties (SOUNDEX, FULL_TEXT, GEO_WITHIN)
- **PredicateResolverMapping**: For property-specific logic where each property has unique behavior
:::

---

## Execution Strategies

### MultiQueryFetchStrategy (RECOMMENDED)

DTO strategy with batch fetching for collections. Avoids N+1 problems.

```java
package io.github.cyfko.filterql.jpa.strategies;

public class MultiQueryFetchStrategy implements ExecutionStrategy<List<Map<String, Object>>> {
    
    /**
     * Constructor with projection class.
     * 
     * @param projectionClass DTO class annotated with @Projection
     */
    public MultiQueryFetchStrategy(Class<?> projectionClass);
    
    /**
     * Constructor with IoC instance resolver.
     * 
     * @param projectionClass DTO class
     * @param instanceResolver resolver for @Computed fields
     */
    public MultiQueryFetchStrategy(
        Class<?> projectionClass, 
        InstanceResolver instanceResolver
    );
}
```

#### Usage

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("active")
    .projection("id", "username", "email", "orders[size=5].id,total")
    .pagination(new Pagination(0, 20, null))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserDTO.class);
QueryExecutor<List<Map<String, Object>>> executor = filterQuery.toExecutor(request);
List<Map<String, Object>> results = executor.executeWith(em, strategy);
```

---

### FullEntityFetchStrategy

Retrieves complete entities without projection.

```java
package io.github.cyfko.filterql.jpa.strategies;

public class FullEntityFetchStrategy<E> implements ExecutionStrategy<List<E>> {
    
    /**
     * @param entityClass JPA entity class
     */
    public FullEntityFetchStrategy(Class<E> entityClass);
}
```

#### Usage

```java
// Request WITHOUT projection
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("active")
    // No .projection() → full entities
    .build();

FullEntityFetchStrategy<User> strategy = new FullEntityFetchStrategy<>(User.class);
QueryExecutor<List<User>> executor = filterQuery.toExecutor(request);
List<User> users = executor.executeWith(em, strategy);
```

---

### CountStrategy

Optimized count query.

```java
package io.github.cyfko.filterql.jpa.strategies;

public class CountStrategy implements ExecutionStrategy<Long> {
    
    /**
     * @param projectionClass DTO class to derive entity
     */
    public CountStrategy(Class<?> projectionClass);
}
```

#### Usage

```java
CountStrategy strategy = new CountStrategy(UserDTO.class);
QueryExecutor<Long> executor = filterQuery.toExecutor(request);
Long count = executor.executeWith(em, strategy);
```

---

### Strategy Comparison

| Strategy | Use Case | Return | Projection | Collections |
|----------|----------|--------|------------|-------------|
| `MultiQueryFetchStrategy` | DTO with collections | `List<Map<String, Object>>` | ✅ | ✅ Batch |
| `FullEntityFetchStrategy` | Complete entities | `List<E>` | ❌ | Via lazy loading |
| `CountStrategy` | Count only | `Long` | ❌ | N/A |

---

## FilterConfig

Filtering behavior configuration.

```java
package io.github.cyfko.filterql.core.config;

public record FilterConfig(
    boolean ignoreCase,           // Case-insensitive comparisons
    NullHandling nullHandling,    // Null sorting
    EnumMatching enumMatching,    // Enum matching strategy
    StringNormalization stringNormalization  // String normalization
) {
    
    public static Builder builder();
    
    public enum NullHandling {
        NULLS_FIRST,
        NULLS_LAST,
        NATIVE      // DB default behavior
    }
    
    public enum EnumMatching {
        NAME,       // Match by Enum.name()
        ORDINAL,    // Match by Enum.ordinal()
        STRING      // Match by toString()
    }
    
    public enum StringNormalization {
        NONE,       // No normalization
        TRIM,       // Remove whitespace
        LOWERCASE,  // Convert to lowercase
        UPPERCASE   // Convert to uppercase
    }
}
```

#### Example

```java
FilterConfig config = FilterConfig.builder()
    .ignoreCase(true)
    .nullHandling(FilterConfig.NullHandling.NULLS_LAST)
    .enumMatching(FilterConfig.EnumMatching.NAME)
    .stringNormalization(FilterConfig.StringNormalization.TRIM)
    .build();

JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    mappingFunction,
    config
);
```

---

## InstanceResolver

Interface for IoC bean resolution (`@Computed` fields).

```java
package io.github.cyfko.filterql.jpa.mappings;

public interface InstanceResolver {
    
    /**
     * Resolves a provider instance from the IoC container.
     * 
     * @param providerClass provider class
     * @return provider instance
     */
    <T> T resolve(Class<T> providerClass);
}
```

---

## PathResolverUtils

Utilities for JPA path navigation.

```java
package io.github.cyfko.filterql.jpa.utils;

public final class PathResolverUtils {
    
    /**
     * Resolves a nested path to a JPA Path.
     * 
     * @param root entity root
     * @param path dot-separated path (e.g., "address.city.name")
     * @return resolved JPA Path
     */
    public static <E> Path<?> resolvePath(Root<E> root, String path);
    
    /**
     * Resolves with generic type.
     */
    public static <E, T> Path<T> resolvePath(Root<E> root, String path, Class<T> type);
}
```

---

## External Annotations

:::note External Dependencies
The following annotations come from [projection-spec](https://github.com/cyfko/projection-spec) and are implemented by [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor).
:::

### @Projection

Defines a DTO class with mapping to a JPA entity.

```java
import io.github.cyfko.projection.Projection;

@Projection(entity = User.class)
public class UserDTO {
    private Long id;
    private String username;
    // ...
}
```

### @Projected

Customizes the mapping of a DTO field.

```java
import io.github.cyfko.projection.Projected;

@Projection(entity = User.class)
public class UserDTO {
    
    @Projected(from = "firstName")  // Explicit mapping
    private String name;
    
    @Projected(from = "address.city.name")  // Nested path
    private String cityName;
}
```

### @Computed

Defines a dynamically computed field.

```java
import io.github.cyfko.projection.Computed;

@Projection(entity = User.class)
public class UserDTO {
    
    @Computed(provider = AgeCalculator.class, method = "calculateAge")
    private Integer age;
}
```

### @Provider

Marks a class as a computed values provider.

```java
import io.github.cyfko.projection.Provider;
import org.springframework.stereotype.Component;

@Provider
@Component
public class AgeCalculator {
    
    public Integer calculateAge(User user) {
        if (user.getBirthDate() == null) return null;
        return Period.between(user.getBirthDate(), LocalDate.now()).getYears();
    }
}
```

---

## Next Steps

- [Spring Adapter Reference](spring-adapter) - Spring Boot Integration
- [Projection Guide](../guides/projection) - Advanced projection syntax
