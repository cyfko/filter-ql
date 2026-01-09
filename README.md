# FilterQL

**Version:** 4.0.0  
**License:** MIT  
**Language:** Java 17+ (Core/JPA: Java 21+)  
**Status:** Production Ready

---

## Protocol Specification

**[FilterQL Protocol (RFC)](PROTOCOL.md)** - Formal protocol specification defining message format, DSL grammar, projection syntax (BNF), validation rules, and interoperability requirements

---

## Module Documentation

FilterQL is organized into multiple modules, each with comprehensive documentation:

### Core & Adapters

- **[FilterQL Core](core/java/README.md)** - Core DSL parsing, validation, SPI contracts, operator registry, and caching system
- **[FilterQL JPA Adapter](adapters/java/filterql-jpa/README.md)** - JPA Criteria API integration, DTO projection, custom predicates, and computed fields
- **[FilterQL Spring Adapter](adapters/java/filterql-spring/README.md)** - Spring Boot integration, annotation processor, PropertyRef generation, REST controller scaffolding
- **[FilterQL Spring Starter](adapters/java/filterql-spring-starter/README.md)** - Convenience dependency aggregator for Spring Boot projects

### Testing & Examples

- **[Integration Tests](integration-test/README.md)** - End-to-end tests, entity fixtures, test scenarios, and usage examples

**Quick Navigation:**
- [Architecture Overview](#architecture-overview) - System design and component responsibilities
- [Core Concepts](#core-concepts) - PropertyReference, FilterDefinition, DSL syntax
- [Usage Guide](#usage-guide) - Code examples and integration patterns
- [Limitations & Constraints](#limitations--constraints) - Known limitations and design trade-offs

---

## Project Overview

FilterQL is a type-safe, framework-agnostic dynamic filtering library for Java enterprise applications. It enables developers to construct complex, composable filter queries using enum-based property references and a boolean Domain-Specific Language (DSL), translating them into backend-specific query predicates (JPA Criteria API, SQL, etc.).

The library solves the problem of building dynamic filters at runtime while maintaining compile-time type safety, preventing common errors such as typos in property names, incompatible operator usage, and type mismatches between properties and values.

**Key Characteristics:**
- **Type-Safe:** Enum-based property references eliminate runtime string errors
- **Composable:** Boolean DSL supports arbitrary complexity with AND, OR, NOT operations
- **Backend-Agnostic:** Core abstractions work with any query technology via adapters
- **Extensible:** Custom operators via Service Provider Interface (SPI)
- **Performance-Optimized:** Structural caching, single-pass DSL parsing, lazy evaluation
- **Production-Ready:** Comprehensive validation, immutable data structures, thread-safe

---

## Architecture Overview

FilterQL follows a layered architecture with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────────┐
│                      APPLICATION LAYER                          │
│  FilterRequest construction, DSL expressions, property enums    │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                       CORE LAYER                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ FilterQuery  │  │  DslParser   │  │ Validation   │         │
│  │   Factory    │  │              │  │   Engine     │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  ┌──────────────────────────────────────────────────┐          │
│  │  FilterRequest → FilterTree → Condition Tree     │          │
│  └──────────────────────────────────────────────────┘          │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                      ADAPTER LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ FilterContext│  │   Predicate  │  │  Execution   │         │
│  │(Backend SPI) │  │   Resolver   │  │  Strategies  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
│                                                                  │
│  Backend-Specific: JPA Adapter, Spring Annotation Processor     │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                    PERSISTENCE LAYER                            │
│     JPA Criteria API, EntityManager, JDBC, MongoDB, etc.        │
└─────────────────────────────────────────────────────────────────┘
```

### Module Responsibilities

#### **1. Core Module (`filterql-core`)**
- **DSL Parsing:** Transforms boolean expressions into filter trees using optimized infix-to-postfix conversion
- **Validation:** Type checking, operator compatibility, value validation
- **Models:** Immutable data structures (`FilterRequest`, `FilterDefinition`, `Pagination`)
- **SPI Contracts:** Interfaces for backend adapters (`FilterContext`, `PredicateResolver`, `ExecutionStrategy`)
- **Operator Registry:** Manages standard and custom operators via `OperatorProviderRegistry`
- **Caching:** Structural caching system for reusing parsed filter structures

#### **2. JPA Adapter (`filterql-jpa`)**
- **JPA Integration:** Translates conditions into JPA Criteria API predicates
- **Projection System:** DTO-first execution plans with batch fetching for collections
- **Path Resolution:** Metadata-driven navigation of entity relationships
- **Custom Predicates:** Support for advanced filter logic beyond simple property mapping
- **Computed Fields:** Integration with IoC containers for dynamic field resolution

#### **3. Spring Adapter (`filterql-spring`)**
- **Annotation Processor:** Generates PropertyRef enums from `@Projection` annotated entities at compile-time
- **FilterQlService:** High-level service for executing filter queries with automatic context resolution
- **REST Controller Generation:** Auto-generates filter endpoints with search, count, and schema introspection
- **Custom Result Mapping:** `PaginatedData<T>` wrapper with `ResultMapper` support for DTO transformation
- **Virtual Fields:** Supports computed fields via annotation-driven provider resolution
- **Spring IoC Integration:** Automatic bean wiring for `FilterContext` and `InstanceResolver`

---

## Core Concepts

### 1. PropertyReference Enum

Property references are type-safe enums that define filterable properties on entities:

```java
public enum UserPropertyRef implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE,
    STATUS,
    CREATED_DATE,
    ACTIVE;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case USERNAME, EMAIL -> String.class;
            case AGE -> Integer.class;
            case STATUS -> UserStatus.class;
            case CREATED_DATE -> LocalDate.class;
            case ACTIVE -> Boolean.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case USERNAME -> Set.of(Op.EQ, Op.MATCHES, Op.IN);
            case EMAIL -> Set.of(Op.EQ, Op.MATCHES);
            case AGE -> Set.of(Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE, Op.RANGE);
            case STATUS -> Set.of(Op.EQ, Op.NE, Op.IN);
            case CREATED_DATE -> Set.of(Op.EQ, Op.GT, Op.LT, Op.RANGE);
            case ACTIVE -> Set.of(Op.EQ);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

### 2. FilterDefinition

Atomic filter unit consisting of property, operator, and value:

```java
// Equality filter
var nameFilter = new FilterDefinition<>(UserPropertyRef.USERNAME, Op.EQ, "john.doe");

// Pattern matching
var emailFilter = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.MATCHES, "%@example.com");

// Range filter
var ageRangeFilter = new FilterDefinition<>(UserPropertyRef.AGE, Op.RANGE, List.of(18, 65));

// Collection membership
var statusFilter = new FilterDefinition<>(
    UserPropertyRef.STATUS, 
    Op.IN, 
    List.of(UserStatus.ACTIVE, UserStatus.PENDING)
);

// Null check (unary operator)
var nullCheckFilter = new FilterDefinition<>(UserPropertyRef.EMAIL, Op.IS_NULL, null);
```

### 3. Boolean DSL

Compose filters using boolean logic:

```
Operator Precedence (highest to lowest):
1. Parentheses: ( )
2. NOT: !
3. AND: &
4. OR: |
```

**Examples:**
```java
// Simple conjunction
"nameFilter & ageFilter"

// Disjunction with negation
"activeFilter | !deletedFilter"

// Complex nested expression
"((nameFilter & ageFilter) | emailFilter) & !deletedFilter"

// Multiple conditions
"(status1 | status2 | status3) & ageRange & !blocked"
```

### 4. FilterRequest

Complete filter specification with filters, DSL expression, projection, and pagination:

```java
FilterRequest<UserPropertyRef> request = FilterRequest.builder()
    // Define atomic filters
    .filter("nameKey", UserPropertyRef.USERNAME, Op.MATCHES, "john%")
    .filter("ageKey", UserPropertyRef.AGE, Op.GTE, 18)
    .filter("statusKey", UserPropertyRef.STATUS, Op.IN, List.of("ACTIVE", "PENDING"))
    
    // Combine with boolean DSL
    .combineWith("(nameKey & ageKey) | statusKey")
    
    // Optional: specify DTO fields to retrieve
    .projection("id", "username", "email", "createdDate")
    
    // Optional: pagination and sorting
    .pagination(new Pagination(
        0,  // page number (0-indexed)
        50, // page size
        List.of(new SortBy("username", "ASC"))
    ))
    
    .build();
```

### 5. FilterContext

Backend-specific bridge converting abstract conditions into executable predicates:

```java
// JPA example: mapping property references to entity paths
FilterContext context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case USERNAME -> "username";
        case EMAIL -> "email";
        case AGE -> "age";
        case STATUS -> "status";
        case CREATED_DATE -> "createdDate";
        case ACTIVE -> "active";
    }
);
```

### 6. Execution Flow

```java
// 1. Create FilterQuery from context
FilterQuery<User> filterQuery = FilterQueryFactory.of(context);

// 2. Build FilterRequest
FilterRequest<UserPropertyRef> request = /* ... */;

// 3. Execute with strategy
EntityManager em = /* ... */;
ExecutionStrategy<List<UserDto>> strategy = new MultiQueryFetchStrategy<>();

List<UserDto> results = filterQuery.execute(request, em, strategy);
```

---

## How It Works

### Phase 1: Request Construction

The application builds a `FilterRequest` containing atomic filters and a boolean DSL expression:

```java
Map<String, FilterDefinition<UserPropertyRef>> filters = Map.of(
    "f1", new FilterDefinition<>(UserPropertyRef.USERNAME, Op.MATCHES, "john%"),
    "f2", new FilterDefinition<>(UserPropertyRef.AGE, Op.GTE, 18),
    "f3", new FilterDefinition<>(UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
);

String dslExpression = "(f1 & f2) | f3";
```

### Phase 2: DSL Parsing

The `DslParser` converts the DSL string into a `FilterTree` using optimized infix-to-postfix conversion:

```
Input:  "(f1 & f2) | f3"

Step 1: Tokenization
Tokens: [ '(', 'f1', '&', 'f2', ')', '|', 'f3' ]

Step 2: Infix to Postfix (Shunting Yard Algorithm)
Postfix: [ 'f1', 'f2', '&', 'f3', '|' ]

Step 3: Tree Construction (Postfix Evaluation)
        OR
       /  \
     AND   f3
    /  \
   f1  f2
```

### Phase 3: Condition Generation

The `FilterTree` resolves filter keys against the `FilterContext`:

```java
// For each filter key, create a Condition
Condition c1 = context.toCondition("f1", UserPropertyRef.USERNAME, "MATCHES");
Condition c2 = context.toCondition("f2", UserPropertyRef.AGE, "GTE");
Condition c3 = context.toCondition("f3", UserPropertyRef.STATUS, "EQ");

// Compose according to tree structure
Condition combined = c1.and(c2).or(c3);
```

### Phase 4: Predicate Resolution

The `FilterContext` converts the condition tree into a `PredicateResolver`:

```java
PredicateResolver<User> resolver = context.toResolver(combined, executionParams);
```

### Phase 5: Query Execution

The `ExecutionStrategy` builds and executes the backend-specific query:

```java
// JPA Criteria API example
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<User> query = cb.createQuery(User.class);
Root<User> root = query.from(User.class);

// Apply filter predicate
query.where(resolver.resolve(root, query, cb));

// Execute
List<User> results = em.createQuery(query).getResultList();
```

---

## Usage Guide

### Basic Filtering Example

```java
// 1. Define property enum
public enum ProductPropertyRef implements PropertyReference {
    NAME,
    PRICE,
    CATEGORY,
    IN_STOCK;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case NAME, CATEGORY -> String.class;
            case PRICE -> BigDecimal.class;
            case IN_STOCK -> Boolean.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case NAME -> Set.of(Op.MATCHES, Op.EQ);
            case PRICE -> Set.of(Op.GT, Op.LT, Op.RANGE);
            case CATEGORY -> Set.of(Op.EQ, Op.IN);
            case IN_STOCK -> Set.of(Op.EQ);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return Product.class;
    }
}

// 2. Create FilterContext
FilterContext context = new JpaFilterContext<>(
    ProductPropertyRef.class,
    ref -> switch (ref) {
        case NAME -> "name";
        case PRICE -> "price";
        case CATEGORY -> "category.name";  // nested property
        case IN_STOCK -> "inStock";
    }
);

// 3. Create FilterQuery
FilterQuery<Product> filterQuery = FilterQueryFactory.of(context);

// 4. Build filter request
FilterRequest<ProductPropertyRef> request = FilterRequest.builder()
    .filter("nameFilter", ProductPropertyRef.NAME, Op.MATCHES, "%laptop%")
    .filter("priceFilter", ProductPropertyRef.PRICE, Op.LT, 2000.00)
    .filter("stockFilter", ProductPropertyRef.IN_STOCK, Op.EQ, true)
    .combineWith("nameFilter & priceFilter & stockFilter")
    .pagination(new Pagination(0, 20, List.of(new SortBy("price", "ASC"))))
    .build();

// 5. Execute
EntityManager em = entityManagerFactory.createEntityManager();
ExecutionStrategy<List<Product>> strategy = new FullEntityFetchStrategy<>();
List<Product> products = filterQuery.execute(request, em, strategy);
```

### Advanced: DTO Projection

```java
// Define DTO
public class ProductDto {
    private Long id;
    private String name;
    private BigDecimal price;
    // getters, setters
}

// Request with projection
FilterRequest<ProductPropertyRef> request = FilterRequest.builder()
    .filter("category", ProductPropertyRef.CATEGORY, Op.EQ, "Electronics")
    .combineWith("category")
    .projection("id", "name", "price")  // only retrieve these fields
    .build();

// Execute with multi-query strategy (optimized for DTOs)
ExecutionStrategy<List<ProductDto>> strategy = new MultiQueryFetchStrategy<>();
List<ProductDto> dtos = filterQuery.execute(request, em, strategy);
```

### Custom Operators

```java
// 1. Implement CustomOperatorProvider
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
            if (!(definition.value() instanceof String searchValue) || searchValue.isBlank()) {
                throw new IllegalArgumentException("SOUNDEX requires non-blank String value");
            }
            
            String fieldName = definition.ref().name().toLowerCase();
            return cb.equal(
                cb.function("SOUNDEX", String.class, root.get(fieldName)),
                cb.function("SOUNDEX", String.class, cb.literal(searchValue))
            );
        };
    }
}

// 2. Register the operator
OperatorProviderRegistry.register(new SoundexOperatorProvider());

// 3. Use in filter
var soundexFilter = new FilterDefinition<>(
    UserPropertyRef.LAST_NAME, 
    "SOUNDEX",  // custom operator
    "Smith"
);
```

### Spring Boot Integration

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private FilterQlService filterQlService;
    
    @PostMapping("/search")
    public PaginatedData<UserDto> searchUsers(
        @RequestBody FilterRequest<UserPropertyRef> request
    ) {
        return filterQlService.search(
            UserDto.class, 
            request,
            value -> objectMapper.convertValue(value, UserDto.class)
        );
    }
}
```

---

## Limitations & Constraints

### Technical Limitations

1. **JPA Dependency in Core SPI**  
   The `PredicateResolver` interface currently uses JPA types (`jakarta.persistence.*`), creating coupling to JPA even in the core module. This is acknowledged as a design constraint (see ADR-003 for planned improvements in v5.0).

2. **Jakarta Persistence 3.1+ Required**  
   The library requires Jakarta Persistence API 3.1 or higher (not compatible with older javax.persistence).

3. **Java Version Requirements**  
   - **Core Module:** Requires Java 21+ (uses records, pattern matching, text blocks)
   - **JPA Adapter:** Requires Java 21+
   - **Spring Adapter:** Requires Java 17+ (Spring Boot 3.3.5 compatibility)

4. **Compile-Time Metamodel Generation**  
   JPA adapter requires compile-time annotation processing to generate metamodel classes for type-safe path resolution.

5. **Single Entity Type per FilterContext**  
   Each `FilterContext` is bound to a single entity type. Cross-entity joins require custom `PredicateResolverMapping` implementations.

### Operational Constraints

1. **EntityManager Thread Safety**  
   `EntityManager` instances passed to execution methods must follow JPA thread-safety rules (typically one per thread/transaction).

2. **Operator-Property Compatibility**  
   Each property reference must explicitly declare supported operators. Attempting unsupported operators results in validation errors.

3. **Value Type Validation**  
   Filter values must be compatible with property types. Type mismatches are detected at resolution time, not construction time (for performance).

4. **DSL Complexity Limits**  
   The DSL parser has configurable depth limits to prevent stack overflow from excessively nested expressions (default: 100 levels).

5. **No Native SQL Generation**  
   The library does not generate native SQL queries directly. All queries go through backend adapters (JPA Criteria API via EntityManager).

### What FilterQL Does NOT Do

- **Schema Migration:** Does not create or alter database schemas
- **ORM Mapping:** Not a replacement for JPA/Hibernate entity mappings
- **Transaction Management:** Does not manage database transactions
- **Connection Pooling:** Does not handle database connection management
- **Query Optimization:** Delegates query optimization to the underlying JPA provider
- **Multi-Database Support:** Each adapter targets a specific backend technology
- **Dynamic Schema Discovery:** Requires explicit property enum definitions
- **Security/Authorization:** Does not enforce data access security policies
- **Caching Beyond Structure:** Only caches filter structure, not query results

---

## Extension & Improvement Notes

### Optional Recommendations (Non-Blocking)

#### 1. Core Architecture Evolution (v5.0 Consideration)

**Observation:** The `PredicateResolver` interface in the core SPI package uses JPA-specific types, creating an architectural coupling.

**Optional Improvement:** Introduce a backend-agnostic predicate abstraction in the core module, with JPA-specific implementations in adapters. This would enable cleaner support for non-JPA backends (MongoDB, Elasticsearch, REST APIs).

**Impact:** Major refactoring requiring adapter migration.

#### 2. Enhanced Error Reporting

**Observation:** Current exceptions provide basic error messages but could be more granular.

**Optional Improvement:** Introduce specialized exception types for different failure modes:
- `PropertyMappingException` for unmapped properties
- `ProjectionMappingException` for projection failures
- `OperatorValidationException` for operator-value mismatches

**Impact:** Improves debugging experience without changing core behavior.

#### 3. Query Result Caching Strategy

**Observation:** The library caches filter structure but not query results.

**Optional Improvement:** Provide optional `CachedExecutionStrategy` implementations that integrate with common caching solutions (Caffeine, Redis, Hazelcast).

**Impact:** Requires careful cache invalidation strategy design.

#### 4. Metrics and Observability

**Observation:** No built-in performance monitoring or execution metrics.

**Optional Improvement:** Add optional integration with Micrometer or similar observability frameworks to track:
- Filter resolution time
- Query execution time
- Cache hit rates
- DSL parsing performance

**Impact:** Useful for production monitoring but adds dependency.

#### 5. GraphQL Adapter

**Observation:** Currently supports JPA via Criteria API and Spring annotation-driven code generation. GraphQL integration would require custom adapter.

**Optional Improvement:** Create dedicated GraphQL adapter translating FilterQL requests into GraphQL queries with field selection and filtering.

**Impact:** Expands ecosystem reach for GraphQL-based architectures.

#### 6. Extended Operator Library

**Observation:** Standard operators cover most use cases, but specialized domains might need more.

**Optional Improvement:** Provide pre-built operator libraries for:
- Geospatial operations (WITHIN_RADIUS, INTERSECTS)
- Full-text search (FULLTEXT, PHRASE_MATCH)
- Array operations (ARRAY_CONTAINS, ARRAY_OVERLAPS)

**Impact:** Reduces boilerplate for common advanced use cases.

#### 7. Filter Request Builder Enhancements

**Observation:** Current builder API is functional but could be more fluent for complex cases.

**Optional Improvement:** Add builder shortcuts for common patterns:
```java
.filterRange("age", UserProperty.AGE, 18, 65)
.filterIn("status", UserProperty.STATUS, Status.ACTIVE, Status.PENDING)
.filterLike("email", UserProperty.EMAIL, "%@company.com")
```

**Impact:** Improves developer ergonomics without changing underlying model.

#### 8. Documentation Expansion

**Optional Improvement:** Add more real-world integration examples:
- Multi-tenant filtering scenarios
- Complex nested entity projections
- Performance tuning guidelines
- Migration guide from raw JPA Criteria

**Impact:** Lowers adoption barrier for new users.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) file for details.

---

## Support & Community

- **GitHub Repository:** https://github.com/cyfko/filter-ql
- **Issue Tracker:** https://github.com/cyfko/filter-ql/issues
- **Maven Central:** `io.github.cyfko:filterql-core:4.0.0`

---

## Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history.

**Current Stable Version:** 4.0.0

**Minimum Requirements:**
- **Core & JPA Adapter:** Java 21+
- **Spring Adapter:** Java 17+ (Spring Boot 3.3.5)
- Jakarta Persistence API 3.1+
- Maven 3.8+ or Gradle 8+

---

## Authors & Contributors

**Lead Developer:** Frank KOSSI (frank.kossi@kunrin.com)  
**Organization:** Kunrin SA  
**Website:** https://www.kunrin.com
