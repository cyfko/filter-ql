---
sidebar_position: 5
---

# Custom Operators

FilterQL allows extending the 14 standard operators with custom operators via the SPI interface `CustomOperatorProvider`.

---

## SPI Interface

```java
package io.github.cyfko.filterql.core.spi;

/**
 * Contract for providing implementations of custom filter operators.
 * Implementations indicate which operator codes they support and provide
 * methods to resolve FilterDefinition instances into executable PredicateResolver.
 */
public interface CustomOperatorProvider {
    
    /**
     * Returns the set of operator codes supported by this provider.
     * Each code must be unique across all registered providers.
     * Use UPPER_SNAKE_CASE convention for consistency.
     * @return a non-null, non-empty set of operator code strings
     */
    Set<String> supportedOperators();
    
    /**
     * Resolves a FilterDefinition into a PredicateResolver for query construction.
     * Value validation should be performed inside the returned PredicateResolver.
     * @param definition the filter definition containing filtering criteria
     * @return a PredicateResolver capable of producing the query predicate
     */
    <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition);
}
```

---

## Registering an Operator

### Via OperatorProviderRegistry

```java
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
import io.github.cyfko.filterql.core.spi.CustomOperatorProvider;

// Register at application startup
OperatorProviderRegistry.register(new SoundexOperatorProvider());
```

### Example Implementation: SOUNDEX

```java
public class SoundexOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("SOUNDEX");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            // Value validation at execution time
            Object value = definition.value();
            if (value == null) {
                throw new IllegalArgumentException("SOUNDEX value cannot be null");
            }
            if (!(value instanceof String strValue)) {
                throw new IllegalArgumentException("SOUNDEX requires a String value");
            }
            if (strValue.isBlank()) {
                throw new IllegalArgumentException("SOUNDEX value cannot be blank");
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            return cb.equal(
                cb.function("SOUNDEX", String.class, root.get(fieldName)),
                cb.function("SOUNDEX", String.class, cb.literal(strValue))
            );
        };
    }
}
```

---

## Custom Operator Examples

### Full-Text Search

```java
public class FullTextOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("FULLTEXT");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            Object value = definition.value();
            if (!(value instanceof String searchQuery)) {
                throw new IllegalArgumentException("FULLTEXT requires a String query");
            }
            
            // Clean and prepare the full-text query
            String cleanedQuery = searchQuery.trim().replaceAll("\\s+", " & ");
            String fieldName = definition.ref().name().toLowerCase();
            
            // PostgreSQL ts_vector / ts_query
            return cb.isTrue(
                cb.function(
                    "to_tsvector",
                    Boolean.class,
                    cb.literal("english"),
                    root.get(fieldName)
                ).in(
                    cb.function(
                        "to_tsquery",
                        Object.class,
                        cb.literal("english"),
                        cb.literal(cleanedQuery)
                    )
                )
            );
        };
    }
}
```

### Geographic Distance

```java
public class GeoDistanceOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("GEO_WITHIN");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            Object value = definition.value();
            if (!(value instanceof Map<?, ?> params)) {
                throw new IllegalArgumentException("GEO_WITHIN requires a Map with lat, lng, radiusKm");
            }
            
            Double lat = (Double) params.get("lat");
            Double lng = (Double) params.get("lng");
            Double radiusKm = (Double) params.get("radiusKm");
            
            if (lat == null || lng == null || radiusKm == null) {
                throw new IllegalArgumentException("lat, lng, and radiusKm are required");
            }
            
            // Simplified Haversine formula
            Expression<Double> distance = cb.function(
                "haversine_distance",
                Double.class,
                root.get("latitude"),
                root.get("longitude"),
                cb.literal(lat),
                cb.literal(lng)
            );
            
            return cb.lessThanOrEqualTo(distance, radiusKm);
        };
    }
}
```

### JSON Operator (PostgreSQL)

```java
public class JsonContainsOperatorProvider implements CustomOperatorProvider {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("JSON_CONTAINS");
    }
    
    @Override
    public <P extends Enum<P> & PropertyReference> 
    PredicateResolver<?> toResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            Object value = definition.value();
            if (!(value instanceof Map<?, ?> jsonValue)) {
                throw new IllegalArgumentException("JSON_CONTAINS requires a Map value");
            }
            
            // Convert to JSON string
            String jsonString;
            try {
                jsonString = objectMapper.writeValueAsString(jsonValue);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to serialize value to JSON", e);
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            // PostgreSQL @> operator
            return cb.isTrue(
                cb.function(
                    "jsonb_contains",
                    Boolean.class,
                    root.get(fieldName),
                    cb.literal(jsonString)
                )
            );
        };
    }
}
```

---

## Spring Boot Registration

```java
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;

@Configuration
public class FilterQlOperatorConfig {
    
    @PostConstruct
    public void registerCustomOperators() {
        OperatorProviderRegistry.register(new SoundexOperatorProvider());
        OperatorProviderRegistry.register(new FullTextOperatorProvider());
        OperatorProviderRegistry.register(new GeoDistanceOperatorProvider());
        OperatorProviderRegistry.register(new JsonContainsOperatorProvider());
    }
}
```

---

## Usage

Once registered, the custom operator can be used like any standard operator:

```java
// Usage with string code
var soundexFilter = new FilterDefinition<>(
    UserPropertyRef.FULL_NAME,
    "SOUNDEX",  // Custom operator code
    "Smith"
);

// In a complete request
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("soundexName", UserPropertyRef.FULL_NAME, "SOUNDEX", "Smith")
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("soundexName & active")
    .build();
```

---

## Best Practices

| Aspect | Recommendation |
|--------|----------------|
| **Naming** | Use descriptive UPPER_CASE codes (SOUNDEX, FULLTEXT, GEO_WITHIN) |
| **Validation** | Validate rigorously inside `toResolver()` at execution time |
| **Error Messages** | Provide clear, actionable error messages |
| **Documentation** | Document expected parameters and behavior |
| **Testing** | Write unit tests for each operator |

---

## Next Steps

- [Core Reference](../reference/core) - Complete core module API
- [JPA Adapter Reference](../reference/jpa-adapter) - PredicateResolver in detail
