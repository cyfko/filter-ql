# FilterQL Core Module

**Version**: 4.0.0  
**License**: MIT  
**Java**: 21+  
**Dependencies**: Jakarta Persistence API 3.1.0, Jakarta Validation API 3.1.1

---

## Overview

FilterQL Core is the foundational module providing framework-agnostic dynamic filtering capabilities through a type-safe Domain-Specific Language (DSL). It defines all core interfaces, SPIs, data models, validation logic, and parsing infrastructure that backend adapters (JPA, SQL, etc.) implement to provide concrete query execution.

**Key Characteristics:**
- **Framework-Agnostic**: No assumptions about query backends
- **Type-Safe**: Compile-time property validation via enums
- **Composable**: Boolean logic operators (AND, OR, NOT) with arbitrary nesting
- **Extensible**: Custom operators via SPI registry
- **High-Performance**: O(n) parsing, structural caching, minimal allocations
- **Thread-Safe**: Immutable data structures, concurrent caching

---

## Table of Contents

1. [Architecture](#architecture)
2. [Core Concepts](#core-concepts)
3. [Public API](#public-api)
4. [Usage Guide](#usage-guide)
5. [Configuration](#configuration)
6. [Custom Operators](#custom-operators)
7. [Performance & Caching](#performance--caching)
8. [Integration Guide](#integration-guide)
9. [Limitations](#limitations)
10. [Troubleshooting](#troubleshooting)

---

## Architecture

### Layered Design

```
┌─────────────────────────────────────────────┐
│           APPLICATION LAYER                 │
│  FilterRequest construction, DSL expressions│
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│           CORE API LAYER                    │
│  FilterQueryFactory, FilterContext, Condition│
│  DslParser, FilterTree                      │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│           SPI LAYER                         │
│  FilterQuery, PredicateResolver,            │
│  ExecutionStrategy, QueryExecutor           │
└────────────────┬────────────────────────────┘
                 │
┌────────────────▼────────────────────────────┐
│           ADAPTER LAYER                     │
│  (Provided by filterql-jpa, etc.)          │
└─────────────────────────────────────────────┘
```

### Package Structure

```
io.github.cyfko.filterql.core
├── api/                        # Core abstractions
│   ├── Condition              # Composable filter conditions
│   ├── DslParser              # DSL parsing contract
│   ├── FilterContext          # Backend bridge interface
│   └── FilterTree             # Parsed AST representation
├── spi/                        # Service Provider Interfaces
│   ├── FilterQuery            # Query lifecycle facade
│   ├── PredicateResolver      # Deferred predicate generator
│   ├── ExecutionStrategy      # Execution strategy contract
│   ├── QueryExecutor          # Query execution coordinator
│   ├── CustomOperatorProvider # Custom operator extension
│   └── OperatorProviderRegistry # Operator registration
├── model/                      # Immutable data structures
│   ├── FilterRequest          # Request container
│   ├── FilterDefinition       # Atomic filter spec
│   ├── Pagination             # Pagination metadata
│   ├── SortBy                 # Sort specification
│   └── QueryExecutionParams   # Execution parameters
├── validation/                 # Type safety & validation
│   ├── PropertyReference      # Enum-based property refs
│   └── Op                     # Standard operators
├── impl/                       # Default implementations
│   └── BasicDslParser         # Default DSL parser
├── parsing/                    # Parsing infrastructure
│   ├── FastPostfixConverter   # Infix-to-postfix conversion
│   ├── PostfixConditionBuilder # Condition tree builder
│   └── BooleanSimplifier      # Expression simplification
├── cache/                      # Caching infrastructure
│   ├── BoundedLRUCache        # LRU cache implementation
│   ├── StructuralNormalizer   # Structure-based cache keys
│   └── DslNormalizer          # DSL normalization
├── config/                     # Configuration policies
│   ├── DslPolicy              # DSL complexity limits
│   ├── CachePolicy            # Cache configuration
│   └── ProjectionPolicy       # Projection behavior
├── utils/                      # Utility classes
│   ├── OperatorValidationUtils # Operator validation
│   ├── TypeConversionUtils    # Type compatibility
│   ├── ClassUtils             # Reflection utilities
│   └── OperatorUtils          # Operator constants
├── exception/                  # Exception hierarchy
│   ├── DSLSyntaxException     # DSL parsing errors
│   ├── FilterValidationException # Validation failures
│   └── FilterDefinitionException # Definition errors
├── projection/                 # Projection utilities
│   └── ProjectionFieldParser  # Field path parsing
└── FilterQueryFactory          # Main factory facade
```

---

## Core Concepts

### 1. PropertyReference

Type-safe enum interface for defining filterable properties.

**Why Enums?**
- Compile-time safety (no typos in property names)
- IDE autocomplete support
- Explicit operator compatibility
- Centralized property metadata

**Implementation Pattern:**

```java
public enum UserPropertyRef implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE,
    STATUS,
    CREATED_DATE;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case USERNAME, EMAIL -> String.class;
            case AGE -> Integer.class;
            case STATUS -> UserStatus.class;
            case CREATED_DATE -> LocalDateTime.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case USERNAME, EMAIL -> Set.of(Op.EQ, Op.MATCHES, Op.IN);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
            case STATUS -> Set.of(Op.EQ, Op.NE, Op.IN, Op.NOT_IN);
            case CREATED_DATE -> Set.of(Op.GT, Op.GTE, Op.LT, Op.LTE, Op.RANGE);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

**Important:** Use switch expressions (shown above), NOT constructors with private fields. The simplified pattern avoids unnecessary state management.

### 2. FilterDefinition

Immutable atomic filter specification.

**Components:**
- `ref`: PropertyReference enum value
- `op`: Operator code (case-insensitive)
- `value`: Filter operand (can be null for unary operators)

**Example:**

```java
var nameFilter = new FilterDefinition<>(
    UserPropertyRef.USERNAME,
    Op.MATCHES,
    "%john%"
);

var ageFilter = new FilterDefinition<>(
    UserPropertyRef.AGE,
    Op.GTE,
    18
);

var nullCheckFilter = new FilterDefinition<>(
    UserPropertyRef.EMAIL,
    Op.IS_NULL,
    null  // Null value required for IS_NULL
);
```

### 3. FilterRequest

Immutable request container combining filters with DSL expression.

**Builder Pattern:**

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("f1", UserPropertyRef.USERNAME, Op.MATCHES, "%john%")
    .filter("f2", UserPropertyRef.AGE, Op.GTE, 18)
    .filter("f3", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("(f1 & f2) | f3")  // DSL expression
    .pagination(new Pagination(0, 20, List.of(new SortBy("username", "ASC"))))
    .projection(List.of("username", "email", "age"))
    .build();
```

**Shorthand Syntax:**

```java
// Combine ALL filters with AND
.combineWith("AND")

// Combine ALL filters with OR
.combineWith("OR")

// Combine ALL filters with AND, then negate
.combineWith("NOT")
```

### 4. DSL Grammar

**Operators:**
- `&` (AND): Precedence 2, left-associative
- `|` (OR): Precedence 1, left-associative
- `!` (NOT): Precedence 3, right-associative
- `( )`: Parentheses for grouping

**EBNF Grammar:**
```
expression := term (OR term)*
term       := factor (AND factor)*
factor     := NOT? (identifier | '(' expression ')')
identifier := [a-zA-Z_][a-zA-Z0-9_]*
```

**Examples:**

```java
// Simple
"active"
"active & premium"
"active | premium"
"!deleted"

// Complex
"(active & premium) | vip"
"!deleted & (active | pending)"
"((a & b) | (c & d)) & !(e | f)"
```

### 5. Operators

**Standard Operators (14 total):**

| Operator | Code | Symbol | Description | Value Type |
|----------|------|--------|-------------|------------|
| EQUALS | EQ | = | Exact match | Single |
| NOT_EQUALS | NE | != | Not equal | Single |
| GREATER_THAN | GT | > | Numeric comparison | Single |
| GREATER_THAN_OR_EQUAL | GTE | >= | Numeric comparison | Single |
| LESS_THAN | LT | < | Numeric comparison | Single |
| LESS_THAN_OR_EQUAL | LTE | <= | Numeric comparison | Single |
| MATCHES | MATCHES | ~ | Pattern matching (SQL LIKE) | Single |
| NOT_MATCHES | NOT_MATCHES | !~ | Negated pattern | Single |
| IN | IN | IN | Set membership | Collection |
| NOT_IN | NOT_IN | NOT IN | Negated membership | Collection |
| IS_NULL | IS_NULL | IS NULL | Null check | Null |
| NOT_NULL | NOT_NULL | IS NOT NULL | Not null check | Null |
| RANGE | RANGE | BETWEEN | Range check | Collection (2 items) |
| NOT_RANGE | NOT_RANGE | NOT BETWEEN | Negated range | Collection (2 items) |

**Operator Utility Constants:**

```java
OperatorUtils.FOR_TEXT      // EQ, NE, MATCHES, NOT_MATCHES, IN, NOT_IN, IS_NULL, NOT_NULL
OperatorUtils.FOR_NUMBER    // EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, RANGE, NOT_RANGE, IS_NULL, NOT_NULL
OperatorUtils.FOR_ENUM      // EQ, NE, IN, NOT_IN, IS_NULL, NOT_NULL
OperatorUtils.FOR_DATE      // EQ, NE, GT, GTE, LT, LTE, RANGE, NOT_RANGE, IS_NULL, NOT_NULL
OperatorUtils.FOR_BOOLEAN   // EQ, NE, IS_NULL, NOT_NULL
```

---

## Public API

### FilterQueryFactory

Main entry point for creating FilterQuery instances.

**Factory Methods:**

```java
// Default parser
FilterQuery<User> query = FilterQueryFactory.of(context);

// Custom parser
FilterQuery<User> query = FilterQueryFactory.of(context, customParser);

// With projection policy
FilterQuery<User> query = FilterQueryFactory.of(context, ProjectionPolicy.strict());

// Full customization
FilterQuery<User> query = FilterQueryFactory.of(context, customParser, projectionPolicy);
```

### FilterQuery

High-level query lifecycle facade.

**Methods:**

```java
public interface FilterQuery<E> {
    // Low-level: Get predicate resolver for manual query building
    <P extends Enum<P> & PropertyReference> 
    PredicateResolver<E> toResolver(FilterRequest<P> request);
    
    // Mid-level: Get executor with strategy control
    <P extends Enum<P> & PropertyReference, R> 
    QueryExecutor<R> toExecutor(FilterRequest<P> request);
}
```

**Usage Example:**

```java
// Low-level approach (maximum control)
FilterQuery<User> filterQuery = FilterQueryFactory.of(context);
PredicateResolver<User> resolver = filterQuery.toResolver(request);

CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<User> query = cb.createQuery(User.class);
Root<User> root = query.from(User.class);
query.where(resolver.resolve(root, query, cb));
List<User> results = em.createQuery(query).getResultList();

// Mid-level approach (strategy-based)
QueryExecutor<UserDto> executor = filterQuery.toExecutor(request);
List<UserDto> dtos = executor.executeWith(em, new MultiQueryFetchStrategy<>());
```

### FilterContext

Backend bridge interface for adapters to implement.

**Core Responsibilities:**
1. Transform FilterDefinition → Condition
2. Convert Condition → PredicateResolver
3. Support deferred value binding for structural caching

**Lifecycle:**

```java
// Phase 1: Create condition structure (no values)
Condition condition = context.toCondition("argKey", propertyRef, "EQ");

// Phase 2: Bind values and resolve to predicate
PredicateResolver<User> resolver = context.toResolver(condition, executionParams);
```

### Condition

Composable boolean filter condition.

**Operations:**

```java
Condition nameCondition = context.toCondition("f1", UserPropertyRef.NAME, "EQ");
Condition ageCondition = context.toCondition("f2", UserPropertyRef.AGE, "GT");
Condition statusCondition = context.toCondition("f3", UserPropertyRef.STATUS, "EQ");

// Boolean combinators
Condition combined = nameCondition
    .and(ageCondition.or(statusCondition));  // (name EQ) AND ((age GT) OR (status EQ))

Condition negated = combined.not();  // NOT((name EQ) AND ((age GT) OR (status EQ)))
```

---

## Usage Guide

### Basic Filtering

```java
// 1. Define PropertyReference enum (see Core Concepts section)
public enum UserPropertyRef implements PropertyReference { /* ... */ }

// 2. Create filter request
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("nameFilter", UserPropertyRef.USERNAME, Op.MATCHES, "%john%")
    .filter("ageFilter", UserPropertyRef.AGE, Op.GTE, 18)
    .combineWith("nameFilter & ageFilter")
    .build();

// 3. Create FilterQuery with adapter's context
FilterContext context = /* adapter-specific context */;
FilterQuery<User> filterQuery = FilterQueryFactory.of(context);

// 4. Execute query (adapter-specific)
// Example with JPA adapter:
PredicateResolver<User> resolver = filterQuery.toResolver(request);
// ... use resolver in JPA Criteria API query
```

### Pagination & Sorting

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("statusFilter", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("statusFilter")
    .pagination(new Pagination(
        0,      // page number (0-based)
        20,     // page size
        List.of(
            new SortBy("username", "ASC"),
            new SortBy("createdDate", "DESC")
        )
    ))
    .build();
```

### Projection (DTO Queries)

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("activeFilter", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("activeFilter")
    .projection(List.of("username", "email", "age"))  // Only fetch these fields
    .build();
```

### Complex Boolean Logic

```java
// (name LIKE '%john%' AND age >= 18) OR (status = PREMIUM AND !deleted)
Map<String, FilterDefinition<UserPropertyRef>> filters = Map.of(
    "f1", new FilterDefinition<>(UserPropertyRef.USERNAME, Op.MATCHES, "%john%"),
    "f2", new FilterDefinition<>(UserPropertyRef.AGE, Op.GTE, 18),
    "f3", new FilterDefinition<>(UserPropertyRef.STATUS, Op.EQ, UserStatus.PREMIUM),
    "f4", new FilterDefinition<>(UserPropertyRef.DELETED, Op.EQ, false)
);

FilterRequest<UserPropertyRef> request = new FilterRequest<>(
    filters,
    "((f1 & f2) | (f3 & !f4))"
);
```

### Collection Operators

```java
// IN operator
FilterDefinition<UserPropertyRef> inFilter = new FilterDefinition<>(
    UserPropertyRef.STATUS,
    Op.IN,
    List.of(UserStatus.ACTIVE, UserStatus.PENDING, UserStatus.TRIAL)
);

// RANGE operator (requires exactly 2 values)
FilterDefinition<UserPropertyRef> rangeFilter = new FilterDefinition<>(
    UserPropertyRef.AGE,
    Op.RANGE,
    List.of(18, 65)  // BETWEEN 18 AND 65
);
```

### Null Checks

```java
// IS_NULL (unary operator - value must be null)
FilterDefinition<UserPropertyRef> nullCheck = new FilterDefinition<>(
    UserPropertyRef.EMAIL,
    Op.IS_NULL,
    null  // Value MUST be null
);

// NOT_NULL (unary operator - value must be null)
FilterDefinition<UserPropertyRef> notNullCheck = new FilterDefinition<>(
    UserPropertyRef.PHONE_NUMBER,
    Op.NOT_NULL,
    null  // Value MUST be null
);
```

### Projection Field Syntax

FilterQL supports advanced projection syntax for collections with inline pagination and sorting options.

#### Basic Syntax

```java
// Simple fields
.projection("id", "name", "email")

// Nested fields
.projection("id", "address.city", "address.country")

// Multi-field with shared prefix (compact syntax)
.projection("id", "address.city,country,postalCode")
// Equivalent to: "id", "address.city", "address.country", "address.postalCode"
```

#### Collection Pagination

```java
// Limit collection size
.projection(
    "id",
    "name",
    "books[size=10].title,year"  // Only first 10 books per author
)

// Page navigation
.projection("books[size=10,page=0].title")  // First page (0-9)
.projection("books[size=10,page=1].title")  // Second page (10-19)
.projection("books[size=10,page=2].title")  // Third page (20-29)
```

#### Collection Sorting

```java
// Single sort field
.projection(
    "id",
    "books[sort=year:desc].title,year"  // Sort by year descending
)

// Multi-column sorting
.projection(
    "id",
    "books[sort=year:desc,title:asc].title,year"  // Year DESC, then title ASC
)
```

#### Combined Pagination & Sorting

```java
// Limit size AND sort
.projection(
    "id",
    "name",
    "books[size=20,page=0,sort=year:desc].title,author,year"
)

// Multiple collections with different options
.projection(
    "id",
    "name",
    "books[size=10,sort=year:desc].title",
    "awards[size=5,sort=year:desc].name,year"
)
```

#### Hierarchical Pagination

```java
// Paginate at multiple nesting levels
.projection(
    "id",
    "name",
    "authors[size=10].name,books[size=5,sort=year:desc].title,year"
    // ↑ 10 authors per publisher
    //                      ↑ 5 books per author, sorted by year
)
```

#### Options Reference

**Collection Options (in square brackets):**

- `size=N` - Limit collection to N items (1-10000, default: 10)
- `page=P` - Fetch page P (0-indexed, default: 0)
- `sort=field:dir` - Sort by field (`asc` or `desc`)
  - Multiple sorts: `sort=field1:asc,field2:desc`

**Syntax Rules:**

1. Options immediately follow collection name: `collection[options]`
2. Multiple options separated by commas: `[size=10,page=0,sort=year:desc]`
3. Field names: alphanumeric, underscore, hyphen only
4. Sort direction: `asc` or `desc` (case-insensitive)
5. Multi-field syntax: comma after last dot (e.g., `prefix.field1,field2`)

**Validation:**

```java
// ❌ INVALID: Conflicting options for same collection
FilterRequest.<UserPropertyRef>builder()
    .projection(
        "books[size=10].title",
        "books[size=20].author"  // ERROR: conflicting size
    )
    .build();

// ✅ VALID: Use multi-field syntax
FilterRequest.<UserPropertyRef>builder()
    .projection(
        "books[size=10,sort=year:desc].title,author"  // OK: single declaration
    )
    .build();

// ✅ VALID: Consistent options
FilterRequest.<UserPropertyRef>builder()
    .projection(
        "books[size=10,sort=year:desc].title",
        "books[size=10,sort=year:desc].author"  // OK: identical options
    )
    .build();
```

**Performance Notes:**

- Collections without options are fetched entirely (no limit)
- Implementations use batch fetching to avoid N+1 queries
- Pagination significantly reduces memory usage for large collections
- Collection pagination is independent of top-level pagination

**Example: Large Collections**

```java
// Author with 1000+ books - only fetch first 20
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("activeFilter", AuthorPropertyRef.ACTIVE, Op.EQ, true)
    .combineWith("activeFilter")
    .projection(
        "id",
        "name",
        "books[size=20,sort=year:desc,title:asc].id,title,year"
    )
    .pagination(new Pagination(0, 50, List.of(new SortBy("name", "ASC"))))
    .build();

// Result: 50 authors × 20 books = 1000 book records
// (instead of 50,000+ without pagination)
```

---

## Configuration

### DslPolicy

Controls DSL complexity limits to prevent DoS attacks.

**Presets:**

```java
// Default: Balanced for most applications
DslPolicy.defaults()
// - maxExpressionLength: 1000 characters

// Strict: For public APIs with untrusted input
DslPolicy.strict()
// - maxExpressionLength: 500 characters

// Lenient: For internal tools with trusted input
DslPolicy.lenient()
// - maxExpressionLength: 5000 characters

// Custom
DslPolicy.builder()
    .maxExpressionLength(2000)
    .build()
```

**Usage:**

```java
DslParser parser = new BasicDslParser(DslPolicy.strict(), CachePolicy.defaults());
FilterQuery<User> query = FilterQueryFactory.of(context, parser);
```

### CachePolicy

Controls structural caching behavior.

**Presets:**

```java
// Default: Balanced caching
CachePolicy.defaults()
// - enabled: true
// - maxSize: 1000 entries

// Strict: Limited cache for memory-constrained environments
CachePolicy.strict()
// - enabled: true
// - maxSize: 100 entries

// Lenient: Aggressive caching for high-traffic applications
CachePolicy.lenient()
// - enabled: true
// - maxSize: 10000 entries

// Disabled: No caching (for testing or debugging)
CachePolicy.disabled()
// - enabled: false

// Custom
CachePolicy.builder()
    .enabled(true)
    .maxSize(5000)
    .build()
```

### ProjectionPolicy

Controls projection field validation and behavior.

**Presets:**

```java
// Default: Balanced validation
ProjectionPolicy.defaults()
// - strict validation enabled

// Strict: Enforce all projection rules
ProjectionPolicy.strict()

// Lenient: Relaxed validation
ProjectionPolicy.lenient()

// Custom
ProjectionPolicy.builder()
    .strictValidation(true)
    .build()
```

---

## Custom Operators

FilterQL supports custom operators via the SPI mechanism.

### 1. Implement CustomOperatorProvider

```java
public class GeoOperatorProvider implements CustomOperatorProvider {
    
    @Override
    public Set<String> supportedOperators() {
        return Set.of("NEAR", "WITHIN_RADIUS", "IN_POLYGON");
    }
    
    @Override
    public <E, P extends Enum<P> & PropertyReference> 
    PredicateResolver<E> toResolver(FilterDefinition<P> definition) {
        String op = definition.op().toUpperCase();
        return switch (op) {
            case "NEAR" -> createNearResolver(definition);
            case "WITHIN_RADIUS" -> createRadiusResolver(definition);
            case "IN_POLYGON" -> createPolygonResolver(definition);
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        };
    }
    
    private <E, P extends Enum<P> & PropertyReference>
    PredicateResolver<E> createNearResolver(FilterDefinition<P> definition) {
        return (root, query, cb) -> {
            // Custom predicate logic
            // Example: ST_Distance(location, point) < threshold
            // ... implementation specific to your backend
            return cb.conjunction(); // placeholder
        };
    }
    
    // Similar implementations for other operators...
}
```

### 2. Register Custom Operator

```java
// Register provider
CustomOperatorProvider geoProvider = new GeoOperatorProvider();
OperatorProviderRegistry.register(geoProvider);

// Use custom operator
FilterDefinition<LocationPropertyRef> customFilter = new FilterDefinition<>(
    LocationPropertyRef.COORDINATES,
    "NEAR",  // Custom operator code
    Map.of("lat", 48.8566, "lon", 2.3522, "distance", 10.0)
);

// Unregister when no longer needed
OperatorProviderRegistry.unregister(geoProvider);

// Or unregister specific operators
OperatorProviderRegistry.unregister(Set.of("NEAR", "WITHIN_RADIUS"));
```

### 3. PropertyReference Integration

```java
public enum LocationPropertyRef implements PropertyReference {
    COORDINATES,
    ADDRESS;

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case COORDINATES -> Set.of(
                Op.EQ, 
                Op.IS_NULL, 
                Op.NOT_NULL
                // Note: Custom operators validated at runtime
            );
            case ADDRESS -> OperatorUtils.FOR_TEXT;
        };
    }
    
    // ... other methods
}
```

**Note:** PropertyReference only validates standard operators (`Op` enum). Custom operators are validated at execution time by the operator provider.

---

## Performance & Caching

### Parsing Performance

**Complexity:** O(n) where n = DSL expression length
**Allocations:** Minimal - no intermediate Token objects or AST nodes
**Optimizations:**
- Single-pass tokenization and parsing
- In-place character array scanning
- Boolean expression simplification
- Fail-fast validation

**Benchmarks (approximate):**
- Simple expression (`a & b`): ~10 μs
- Medium complexity (`(a & b) | (c & d)`): ~20 μs
- High complexity (50+ terms): ~200 μs

### Structural Caching

**Cache Level:** `FilterTree.generate()` method (NOT `parse()`)

**Cache Key:** Structural signature based on:
1. Filter definitions (property refs + operators, NO values)
2. Simplified postfix DSL expression
3. Structural normalization

**Cache Hit:** Skips entire Condition tree construction, returns cached result instantly.

**Thread Safety:** Uses ReadWriteLock for concurrent access.

**Memory Management:** Bounded LRU eviction when cache is full.

**Cache Effectiveness:**

```java
// These expressions share the same cache entry:
"(f1 & f2) | f3"
"((f1 & f2) | f3)"      // Extra parentheses - normalized
"f1 & f2 | f3"          // Different precedence interpretation - different structure
```

**Configuration:**

```java
// Enable caching with custom size
CachePolicy cachePolicy = CachePolicy.builder()
    .enabled(true)
    .maxSize(5000)
    .build();

DslParser parser = new BasicDslParser(DslPolicy.defaults(), cachePolicy);
```

**When to Disable Caching:**
- Testing environments (avoid cache pollution)
- Debugging (easier to trace execution)
- Memory-constrained systems
- Workloads with no repeated filter structures

### Memory Footprint

**FilterRequest:**
- Base: ~200 bytes
- Per filter: ~150 bytes
- DSL expression: ~2x expression length

**Cached Condition:**
- Simple: ~100 bytes
- Complex (20 terms): ~2 KB

**LRU Cache:**
- Default (1000 entries): ~150-300 KB
- Lenient (10000 entries): ~1.5-3 MB

---

## Integration Guide

### For Adapter Developers

#### 1. Implement FilterContext

```java
public class MyBackendFilterContext implements FilterContext {
    
    @Override
    public Condition toCondition(String argKey, Enum<?> propertyRef, String op) {
        // Validate property and operator compatibility
        PropertyReference ref = (PropertyReference) propertyRef;
        Op operator = Op.fromString(op);
        
        // Check if standard operator
        if (operator != null) {
            ref.validateOperator(operator);
        } else {
            // Check custom operator registry
            var provider = OperatorProviderRegistry.getProvider(op)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operator: " + op));
        }
        
        // Return deferred condition (no value yet)
        return new MyBackendCondition(argKey, ref, op);
    }
    
    @Override
    public <E> PredicateResolver<E> toResolver(
            Class<E> entityClass,
            Condition condition,
            QueryExecutionParams params) {
        
        // Convert abstract Condition tree to backend-specific predicate
        MyBackendCondition backendCondition = (MyBackendCondition) condition;
        Map<String, Object> arguments = params.argumentRegistry();
        
        // Bind values from argument registry
        Object value = arguments.get(backendCondition.getArgKey());
        
        // Return executable predicate resolver
        return (root, query, cb) -> {
            // Backend-specific predicate construction
            // Example for JPA:
            // return cb.equal(root.get("propertyName"), value);
            return cb.conjunction(); // placeholder
        };
    }
}
```

#### 2. Implement ExecutionStrategy

```java
public class MyCustomStrategy<R> implements ExecutionStrategy<R> {
    
    @Override
    public R execute(
            EntityManager em,
            PredicateResolver<?> resolver,
            QueryExecutionParams params) {
        
        // Access execution parameters
        List<String> projection = params.projection();
        Pagination pagination = params.pagination();
        
        // Build and execute query using resolver
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<R> query = cb.createQuery(/* result type */);
        Root<?> root = query.from(/* entity class */);
        
        // Apply predicate
        query.where(resolver.resolve(root, query, cb));
        
        // Apply pagination/sorting if needed
        // Apply projection if needed
        
        // Execute and return results
        return em.createQuery(query).getResultList();
    }
}
```

#### 3. Testing

```java
@Test
void testCustomAdapter() {
    // Setup
    FilterContext context = new MyBackendFilterContext();
    FilterQuery<User> filterQuery = FilterQueryFactory.of(context);
    
    // Build request
    FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
        .filter("f1", UserPropertyRef.USERNAME, Op.EQ, "john")
        .combineWith("f1")
        .build();
    
    // Resolve
    PredicateResolver<User> resolver = filterQuery.toResolver(request);
    
    // Verify (adapter-specific verification)
    assertNotNull(resolver);
    // ... additional assertions
}
```

---

## Limitations

### 1. PropertyReference Constraints

**❌ Private Fields Pattern (Prohibited):**

```java
// DO NOT USE THIS PATTERN
public enum UserPropertyRef implements PropertyReference {
    USERNAME(String.class, Set.of(Op.EQ));
    
    private final Class<?> type;  // ❌ Prohibited
    private final Set<Op> operators;  // ❌ Prohibited
    
    UserPropertyRef(Class<?> type, Set<Op> operators) {  // ❌ Prohibited
        this.type = type;
        this.operators = operators;
    }
    
    @Override
    public Class<?> getType() {
        return type;  // ❌ Returns field
    }
}
```

**✅ Correct Pattern (Switch Expressions):**

```java
public enum UserPropertyRef implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE;
    
    @Override
    public Class<?> getType() {
        return switch (this) {  // ✅ Switch expression
            case USERNAME, EMAIL -> String.class;
            case AGE -> Integer.class;
        };
    }
    
    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {  // ✅ Switch expression
            case USERNAME, EMAIL -> OperatorUtils.FOR_TEXT;
            case AGE -> OperatorUtils.FOR_NUMBER;
        };
    }
}
```

### 2. DSL Limitations

- **No String Literals:** DSL identifiers only, no quoted strings
- **No Numeric Literals:** Filter values provided separately in FilterDefinition
- **No Custom Infix Operators:** Only `&`, `|`, `!`, `( )`
- **Case-Sensitive Identifiers:** Filter keys are case-sensitive
- **No Comments:** DSL expressions do not support comments

### 3. Operator Validation

- **Standard Operators:** Validated at FilterDefinition construction
- **Custom Operators:** Validated at execution time (deferred)
- **Operator-Value Compatibility:** Checked during condition resolution

### 4. Type System

- **Compile-Time Safety:** Limited to PropertyReference enum declarations
- **Runtime Type Checking:** Performed during predicate resolution
- **Generic Type Erasure:** Collection element types checked via reflection

### 5. Caching Limitations

- **Structure-Only:** Cache keys based on structure, not values
- **No TTL:** Entries evicted only via LRU (no time-based expiration)
- **Single JVM:** No distributed caching support
- **Manual Invalidation:** No automatic cache invalidation mechanism

---

## Troubleshooting

### DSLSyntaxException

**Cause:** Malformed DSL expression

**Common Errors:**

```java
// Missing closing parenthesis
"(f1 & f2"  // ❌

// Invalid identifier characters
"f1 & f-2"  // ❌ (hyphens not allowed)

// Empty expression
""  // ❌

// Operator without operands
"& f1"  // ❌
```

**Solution:** Validate DSL syntax, use shorthand syntax for simple cases.

### FilterValidationException

**Cause:** Invalid operator-property-value combination

**Common Errors:**

```java
// Unsupported operator for property
new FilterDefinition<>(UserPropertyRef.USERNAME, Op.GT, "john")  // ❌ GT not for text

// Wrong value type
new FilterDefinition<>(UserPropertyRef.AGE, Op.EQ, "not a number")  // ❌

// RANGE operator without 2 values
new FilterDefinition<>(UserPropertyRef.AGE, Op.RANGE, List.of(18))  // ❌

// IS_NULL with non-null value
new FilterDefinition<>(UserPropertyRef.EMAIL, Op.IS_NULL, "value")  // ❌
```

**Solution:** Check PropertyReference operator support, validate value types.

### FilterDefinitionException

**Cause:** Invalid filter definition structure

**Common Errors:**

```java
// Null property reference
new FilterDefinition<>(null, Op.EQ, "value")  // ❌

// Null operator
new FilterDefinition<>(UserPropertyRef.NAME, (String) null, "value")  // ❌

// Reserved "CUSTOM" keyword
new FilterDefinition<>(UserPropertyRef.NAME, "CUSTOM", "value")  // ❌
```

**Solution:** Ensure all required fields are non-null, avoid reserved keywords.

### NullPointerException During Resolution

**Cause:** Missing filter key in request

**Example:**

```java
// DSL references "f3" but only f1 and f2 defined
Map<String, FilterDefinition<UserPropertyRef>> filters = Map.of(
    "f1", /* ... */,
    "f2", /* ... */
);
FilterRequest<UserPropertyRef> request = new FilterRequest<>(
    filters,
    "f1 & f2 & f3"  // ❌ f3 not defined
);
```

**Solution:** Ensure all filter keys referenced in DSL exist in the filter map.

### Cache Behavior Issues

**Symptom:** Unexpected filter results or stale data

**Cause:** Structure-based caching may be caching incorrect structures

**Debugging:**

```java
// Temporarily disable caching
CachePolicy noCachePolicy = CachePolicy.disabled();
DslParser parser = new BasicDslParser(DslPolicy.defaults(), noCachePolicy);
FilterQuery<User> query = FilterQueryFactory.of(context, parser);
```

**Solution:** If issue disappears with caching disabled, report as a bug with filter structure details.

---

## Maven Dependency

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-core</artifactId>
    <version>4.0.0</version>
</dependency>
```

---

## Next Steps

- **[JPA Adapter](../adapters/java/filterql-jpa/README.md)**: JPA Criteria API integration, projection strategies, custom predicates
- **[Spring Integration](../adapters/java/filterql-spring/README.md)**: Annotation processor, FilterQlService, REST controllers
- **[Spring Starter](../adapters/java/filterql-spring-starter/README.md)**: Convenience dependency aggregator

---

## License

MIT License - See [LICENSE](../../LICENSE) file for details.

---

## Author

**Frank KOSSI** - frank.kossi@kunrin.com  
© 2024 Kunrin SA
