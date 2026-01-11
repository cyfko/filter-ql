---
sidebar_position: 5
---

# Custom Operators

FilterQL provides 14 standard operators for most filtering needs. For advanced use cases requiring custom filter logic, the **JPA Adapter** offers two complementary approaches:

1. **`CustomOperatorResolver`** - Centralized handler for operators that apply to multiple properties
2. **`PredicateResolverMapping`** - Per-property custom logic for property-specific behavior

:::tip Spring Simplified Syntax
When using the **Spring Adapter** with `@ExposedAs`, you can use a simpler syntax:

```java
@ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
public static PredicateResolver<User> fullNameSearch(String op, Object[] args) {
    return (root, query, cb) -> { /* your logic */ };
}
```

See [Spring Adapter - Virtual Fields](../reference/spring-adapter#virtual-fields) for details.
:::

---

## Approach 1: CustomOperatorResolver

The `CustomOperatorResolver` interface provides a centralized way to handle custom operators. It's called before the default resolution mechanism, allowing you to intercept any operator for any property.

**Best for:** Operators like SOUNDEX, FULL_TEXT, GEO_WITHIN that apply to multiple properties with similar logic.

### Interface

```java
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

### Basic Usage

```java
JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
        UserProperty.class, 
        mappingBuilder
    ).withCustomOperatorResolver((ref, op, args) -> {
        // Return null to delegate to default handling
        if (!"SOUNDEX".equals(op)) {
            return null;
        }
        
        // Handle SOUNDEX operator for applicable properties
        String fieldPath = switch (ref) {
            case FIRST_NAME -> "firstName";
            case LAST_NAME -> "lastName";
            default -> throw new IllegalArgumentException(
                "SOUNDEX not supported for " + ref);
        };
        
        return (root, query, cb) -> cb.equal(
            cb.function("SOUNDEX", String.class, root.get(fieldPath)),
            cb.function("SOUNDEX", String.class, cb.literal((String) args[0]))
        );
    });
```

### Multiple Custom Operators

```java
CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
    return switch (op) {
        case "SOUNDEX" -> handleSoundex(ref, args);
        case "LEVENSHTEIN" -> handleLevenshtein(ref, args);
        case "GEO_WITHIN" -> handleGeoWithin(ref, args);
        case "FULL_TEXT" -> handleFullText(ref, args);
        default -> null;  // Delegate to default handling
    };
};

JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
        UserProperty.class, 
        mappingBuilder
    ).withCustomOperatorResolver(resolver);
```

### Override Standard Operators

You can override standard operators for specific properties:

```java
CustomOperatorResolver<UserProperty> resolver = (ref, op, args) -> {
    // Make MATCHES case-insensitive for EMAIL
    if (ref == UserProperty.EMAIL && "MATCHES".equals(op)) {
        return (root, query, cb) -> {
            String pattern = ((String) args[0]).toLowerCase();
            return cb.like(cb.lower(root.get("email")), pattern);
        };
    }
    return null;
};
```

---

## Approach 2: PredicateResolverMapping

Custom operators are implemented directly in the `JpaFilterContext` mapping function using `PredicateResolverMapping<E>`. This approach provides full control over predicate generation.

**Best for:** Property-specific logic like multi-field search (FULL_NAME), calculated fields, or subqueries that are unique to a single property.

### Interface

```java
package io.github.cyfko.filterql.jpa.mappings;

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

---

## Implementation Examples

### SOUNDEX Operator

```java
// Define in JpaFilterContext mapping
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case USERNAME -> "username";  // Simple path mapping
        case EMAIL -> "email";
        
        // Custom SOUNDEX operator
        case LAST_NAME -> new PredicateResolverMapping<User>() {
            @Override
            public PredicateResolver<User> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    if (!"SOUNDEX".equals(op)) {
                        // Fall back to standard equality for other operators
                        return cb.equal(root.get("lastName"), args[0]);
                    }
                    
                    String searchValue = (String) args[0];
                    if (searchValue == null || searchValue.isBlank()) {
                        throw new IllegalArgumentException("SOUNDEX requires non-blank value");
                    }
                    
                    return cb.equal(
                        cb.function("SOUNDEX", String.class, root.get("lastName")),
                        cb.function("SOUNDEX", String.class, cb.literal(searchValue))
                    );
                };
            }
        };
    }
);
```

### Full-Text Search

```java
case DESCRIPTION -> new PredicateResolverMapping<Product>() {
    @Override
    public PredicateResolver<Product> map(String op, Object[] args) {
        return (root, query, cb) -> {
            if (!"FULLTEXT".equals(op)) {
                return cb.like(root.get("description"), "%" + args[0] + "%");
            }
            
            String searchQuery = (String) args[0];
            String cleanedQuery = searchQuery.trim().replaceAll("\\s+", " & ");
            
            // PostgreSQL ts_vector / ts_query
            return cb.isTrue(
                cb.function(
                    "to_tsvector",
                    Boolean.class,
                    cb.literal("english"),
                    root.get("description")
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
};
```

### Geographic Distance (GEO_WITHIN)

```java
case LOCATION -> new PredicateResolverMapping<Store>() {
    @Override
    public PredicateResolver<Store> map(String op, Object[] args) {
        return (root, query, cb) -> {
            if (!"GEO_WITHIN".equals(op)) {
                throw new IllegalArgumentException("LOCATION only supports GEO_WITHIN operator");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Double> params = (Map<String, Double>) args[0];
            
            Double lat = params.get("lat");
            Double lng = params.get("lng");
            Double radiusKm = params.get("radiusKm");
            
            if (lat == null || lng == null || radiusKm == null) {
                throw new IllegalArgumentException("lat, lng, and radiusKm are required");
            }
            
            // Haversine distance function (database-specific)
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
};
```

### JSON Contains (PostgreSQL)

```java
case METADATA -> new PredicateResolverMapping<Document>() {
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public PredicateResolver<Document> map(String op, Object[] args) {
        return (root, query, cb) -> {
            if (!"JSON_CONTAINS".equals(op)) {
                throw new IllegalArgumentException("METADATA only supports JSON_CONTAINS");
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonValue = (Map<String, Object>) args[0];
            
            String jsonString;
            try {
                jsonString = objectMapper.writeValueAsString(jsonValue);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to serialize to JSON", e);
            }
            
            // PostgreSQL @> operator
            return cb.isTrue(
                cb.function(
                    "jsonb_contains",
                    Boolean.class,
                    root.get("metadata"),
                    cb.literal(jsonString)
                )
            );
        };
    }
};
```

---

## Multi-Field Search

A common use case is searching across multiple fields:

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

---

## Usage in FilterRequest

Once configured in `JpaFilterContext`, custom operators are used like standard operators:

```java
// Using custom SOUNDEX operator
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("soundexName", UserPropertyRef.LAST_NAME, "SOUNDEX", "Smith")
    .filter("active", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("soundexName & active")
    .build();

// Using custom GEO_WITHIN operator
FilterRequest<StorePropertyRef> geoRequest = FilterRequest.<StorePropertyRef>builder()
    .filter("nearby", StorePropertyRef.LOCATION, "GEO_WITHIN", 
        Map.of("lat", 48.8566, "lng", 2.3522, "radiusKm", 10.0))
    .combineWith("nearby")
    .build();
```

---

## Best Practices

| Aspect | Recommendation |
|--------|----------------|
| **Naming** | Use descriptive UPPER_CASE codes (SOUNDEX, FULLTEXT, GEO_WITHIN) |
| **Validation** | Validate arguments inside the PredicateResolver |
| **Error Messages** | Provide clear, actionable error messages |
| **Fallback** | Consider handling standard operators as fallback |
| **Testing** | Write unit tests for each custom mapping |
| **Documentation** | Document expected parameter types and formats |

---

## Helper Methods

For reusable custom operators, create static helper methods:

```java
public final class CustomMappings {
    
    private CustomMappings() {}
    
    /**
     * Creates a SOUNDEX mapping for any string field.
     */
    public static <E> PredicateResolverMapping<E> soundexMapping(String fieldName) {
        return new PredicateResolverMapping<>() {
            @Override
            public PredicateResolver<E> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    String searchValue = (String) args[0];
                    return cb.equal(
                        cb.function("SOUNDEX", String.class, root.get(fieldName)),
                        cb.function("SOUNDEX", String.class, cb.literal(searchValue))
                    );
                };
            }
        };
    }
    
    /**
     * Creates a case-insensitive LIKE mapping.
     */
    public static <E> PredicateResolverMapping<E> caseInsensitiveLike(String fieldName) {
        return new PredicateResolverMapping<>() {
            @Override
            public PredicateResolver<E> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    String pattern = "%" + ((String) args[0]).toLowerCase() + "%";
                    return cb.like(cb.lower(root.get(fieldName)), pattern);
                };
            }
        };
    }
}

// Usage
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case LAST_NAME -> CustomMappings.soundexMapping("lastName");
        case USERNAME -> CustomMappings.caseInsensitiveLike("username");
        default -> ref.name().toLowerCase();
    }
);
```

---

## Next Steps

- [Core Reference](../reference/core) - Complete core module API
- [JPA Adapter Reference](../reference/jpa-adapter) - PredicateResolverMapping in detail
