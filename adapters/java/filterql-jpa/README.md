# FilterQL JPA Adapter

**Version**: 2.0.0  
**License**: MIT  
**Java**: 21+  
**Dependencies**: FilterQL Core 4.0.0, Jakarta Persistence API 3.1.0

---

## Overview

The JPA Adapter translates FilterQL's framework-agnostic filter model into executable JPA Criteria API predicates. It provides advanced projection strategies, custom predicate mapping, computed field support, and optimized multi-query batch fetching for DTO projections.

**Key Features:**
- JPA Criteria API translation
- DTO-first projection with automatic batching
- Custom predicate resolution
- Computed fields with IoC integration
- Metadata-driven path resolution (no runtime reflection)
- Multiple execution strategies (full entity, multi-query, count)

---

## Architecture

### Core Components

```
┌──────────────────────────────────────────┐
│        JpaFilterContext<P>               │
│  Translates filters to JPA predicates    │
└────────────────┬─────────────────────────┘
                 │
     ┌───────────┴───────────┐
     │                       │
     ▼                       ▼
┌─────────────┐    ┌──────────────────────┐
│ JpaCondition│    │PredicateResolverMapping│
│  (Wrapper)  │    │ (Custom predicates)  │
└─────────────┘    └──────────────────────┘
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
┌──────────────────────┐    ┌───────────────────────┐
│ PathResolverUtils    │    │ MultiQueryExecutionPlan│
│ (Nested navigation)  │    │ (Batch projection)    │
└──────────────────────┘    └───────────────────────┘
```

### Execution Strategies

1. **FullEntityFetchStrategy**: Returns complete entities without projection
2. **MultiQueryFetchStrategy**: DTO projection with batched collection fetching
3. **TypedMultiQueryFetchStrategy**: Typed DTO projection (maps to concrete DTOs)
4. **CountStrategy**: Optimized count queries

---

## Quick Start

### 1. Define PropertyReference

```java
public enum UserPropertyRef implements PropertyReference {
    USERNAME,
    EMAIL,
    AGE,
    CITY;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case USERNAME, EMAIL -> String.class;
            case AGE -> Integer.class;
            case CITY -> String.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case USERNAME, EMAIL -> OperatorUtils.FOR_TEXT;
            case AGE -> OperatorUtils.FOR_NUMBER;
            case CITY -> OperatorUtils.FOR_TEXT;
        };
    }

    @Override
    public Class<?> getEntityType() {
        return User.class;
    }
}
```

### 2. Create JpaFilterContext

```java
// Simple path mapping
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case USERNAME -> "username";
        case EMAIL -> "email";
        case AGE -> "age";
        case CITY -> "address.city.name";  // Nested property
    }
);

// With custom FilterConfig
FilterConfig config = FilterConfig.builder()
    .ignoreCase(true)
    .nullHandling(FilterConfig.NullHandling.NULLS_LAST)
    .build();

JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    mappingFunction,
    config
);
```

### 3. Build FilterQuery

```java
FilterQuery<User> filterQuery = FilterQueryFactory.of(context);
```

### 4. Execute Query

**Option A: Using ExecutionStrategy (Recommended)**

The strategy handles all JPA Criteria API operations internally:

```java
// Build filter request with DTO projection
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("f1", UserPropertyRef.USERNAME, Op.MATCHES, "%john%")
    .filter("f2", UserPropertyRef.AGE, Op.GTE, 18)
    .combineWith("f1 & f2")
    .projection("id", "username", "email", "age")  // DTO fields
    .pagination(new Pagination(0, 20, List.of(new SortBy("username", "ASC"))))
    .build();

// Recommended: MultiQueryFetchStrategy for DTO projection with batch optimization
QueryExecutor<List<Map<String, Object>>> executor = filterQuery.toExecutor(request);
List<Map<String, Object>> results = executor.executeWith(em, new MultiQueryFetchStrategy(UserDTO.class));

// Alternative: FullEntityFetchStrategy for simple cases (no projection, returns full entities)
QueryExecutor<List<User>> entityExecutor = filterQuery.toExecutor(requestWithoutProjection);
List<User> users = entityExecutor.executeWith(em, new FullEntityFetchStrategy<>(User.class));
```

**Option B: Manual PredicateResolver (Advanced)**

For custom query construction or when you need full control:

```java
// Get resolver only
PredicateResolver<User> resolver = filterQuery.toResolver(request);

// Manually construct JPA Criteria query
CriteriaBuilder cb = em.getCriteriaBuilder();
CriteriaQuery<User> query = cb.createQuery(User.class);
Root<User> root = query.from(User.class);

// Apply filter predicate
query.where(resolver.resolve(root, query, cb));

// Add custom joins, selections, etc.
// query.select(...).distinct(true)...

// Execute
List<User> users = em.createQuery(query).getResultList();
```

**When to use each approach:**
- **Strategy (Option A)**: Standard use cases → Strategy handles all JPA operations internally
  - `MultiQueryFetchStrategy`: DTO projections with batch optimization (RECOMMENDED)
  - `FullEntityFetchStrategy`: Simple entity queries without projection
  - `CountStrategy`: Count-only queries
- **Manual (Option B)**: Custom requirements → Full control of JPA Criteria API
  - Complex joins, subqueries
  - Specific JPA features (fetch joins, entity graphs)
  - Performance tuning with custom query hints

---

## Custom Predicate Mapping

For complex filtering logic beyond simple path mapping, use `PredicateResolverMapping`:

```java
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case USERNAME -> "username";
        case EMAIL -> "email";
        
        // Custom full-name search across multiple fields
        case FULL_NAME -> (PredicateResolverMapping<User>) (op, args) -> (root, query, cb) -> {
            String search = (String) args[0];
            return cb.or(
                cb.like(root.get("firstName"), "%" + search + "%"),
                cb.like(root.get("lastName"), "%" + search + "%")
            );
        };
        
        default -> ref.name().toLowerCase();
    }
);
```

---

## DTO Projection

### Define Projection Metadata

Use the `@Projection` annotation from [projection-spec](https://github.com/cyfko/projection-spec):

**Note:** The `@Projection` annotation is **not part of FilterQL**. It comes from the external [projection-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor) library which implements the [projection-spec](https://github.com/cyfko/projection-spec).

```java
import io.github.cyfko.projection.Projection;  // External dependency

@Projection(entity = User.class)
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    
    // Getters/setters...
}
```

### Execute with MultiQueryFetchStrategy

```java
// Simple projection
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("activeFilter", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("activeFilter")
    .projection("id", "username", "email", "age")
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(UserDTO.class);
QueryExecutor<List<Map<String, Object>>> executor = filterQuery.toExecutor(request);
List<Map<String, Object>> results = executor.executeWith(em, strategy);
```

---

## Collection Projection with Pagination & Sorting

FilterQL supports projecting collections (`@OneToMany`, `@ManyToMany`) with **inline pagination and sorting options** using advanced projection field syntax.

### Projection Field Syntax

Collections can be paginated and sorted using bracketed options directly in projection strings:

**Basic Syntax:**
```
collectionName[size=N,page=P,sort=field:dir].nestedField
```

**Supported Options:**
- `size=N` - Page size (1 to 10000, default: 10)
- `page=P` - Page number (0-indexed, default: 0)
- `sort=field:dir` - Sort by field with direction (`asc` or `desc`)
  - Multiple sort fields: `sort=field1:asc,field2:desc`

### Entity with Collections

```java
@Entity
public class Author {
    @Id
    private Long id;
    private String name;
    
    @OneToMany(mappedBy = "author")
    private List<Book> books;
    
    @ManyToMany
    @JoinTable(name = "author_awards")
    private Set<Award> awards;
}

@Entity
public class Book {
    @Id
    private Long id;
    private String title;
    private Integer year;
    
    @ManyToOne
    private Author author;
}
```

### DTO with Collection Projections

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Projected;

@Projection(entity = Author.class)
public class AuthorDTO {
    private Long id;
    private String name;
    
    // Collection projection with nested DTO
    @Projected(from = "books")
    private List<BookSummaryDTO> books;
    
    @Projected(from = "awards")
    private Set<AwardDTO> awards;
}

@Projection(entity = Book.class)
public class BookSummaryDTO {
    private Long id;
    private String title;
    private Integer year;
}
```

### Simple Collection Projection

**Without pagination (fetch all):**

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection("id", "name", "books.id,title,year", "awards.name")
    .pagination(new Pagination(0, 20, List.of(new SortBy("name", "ASC"))))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(AuthorDTO.class);
QueryExecutor<List<Map<String, Object>>> executor = filterQuery.toExecutor(request);
List<Map<String, Object>> results = executor.executeWith(em, strategy);

// Result: All books and awards for each author (no limit)
```

### Paginated Collection Projection

**Limit books per author:**

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection(
        "id",
        "name",
        "books[size=10].id,title,year",  // Only 10 books per author
        "awards.name"
    )
    .pagination(new Pagination(0, 20, List.of(new SortBy("name", "ASC"))))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(AuthorDTO.class);
List<Map<String, Object>> results = executor.executeWith(em, strategy);

// Result structure:
// [
//   {
//     "id": 1,
//     "name": "John Smith",
//     "books": [
//       {"id": 101, "title": "Book 1", "year": 2023},  // Only first 10 books
//       {"id": 102, "title": "Book 2", "year": 2022}
//     ],
//     "awards": [
//       {"id": 1, "name": "Best Author 2023"},
//       {"id": 2, "name": "Novel Prize 2022"}
//     ]
//   }
// ]
```

### Sorted Collection Projection

**Sort books by year descending:**

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection(
        "id",
        "name",
        "books[sort=year:desc].id,title,year",  // Sort by year DESC
        "awards[sort=year:desc].name,year"      // Sort awards by year DESC
    )
    .build();
```

### Combined Pagination & Sorting

**Paginate AND sort collections:**

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection(
        "id",
        "name",
        "books[size=10,page=0,sort=year:desc].id,title,year",  // 10 books, sorted by year DESC
        "awards[size=5,sort=name:asc].name"                     // 5 awards, sorted by name ASC
    )
    .build();
```

### Multi-Field Sort

**Sort by multiple columns:**

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .projection(
        "id",
        "name",
        "books[size=20,sort=year:desc,title:asc].id,title,year"  // Sort by year DESC, then title ASC
    )
    .build();
```

### Hierarchical Pagination

**Paginate nested collections at multiple levels:**

```java
@Entity
public class Publisher {
    @OneToMany
    private List<Author> authors;
}

@Entity  
public class Author {
    @OneToMany
    private List<Book> books;
}

// Paginate both authors AND books collections
FilterRequest<PublisherPropertyRef> request = FilterRequest.<PublisherPropertyRef>builder()
    .projection(
        "id",
        "name",
        "authors[size=10].id,name,books[size=5,sort=year:desc].title,year"
        // ↑ 10 authors per publisher
        //                          ↑ 5 books per author, sorted by year
    )
    .build();
```

### Performance Considerations

**Batch Fetching:**
- `MultiQueryFetchStrategy` uses separate optimized queries for collections
- Avoids N+1 problem via batch fetching
- Collections fetched in batches of 10 entities by default

**Pagination Benefits:**
- Reduces memory footprint for large collections
- Improves initial response time
- Supports "load more" patterns in UI

**Example: Large Collections**

```java
// Author with 1000+ books - only fetch first 20
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("f1", AuthorPropertyRef.ACTIVE, Op.EQ, true)
    .combineWith("f1")
    .projection(
        "id",
        "name",
        "books[size=20,sort=year:desc,title:asc].id,title,year"  // Only 20 books per author
    )
    .pagination(new Pagination(0, 50, List.of(new SortBy("name", "ASC"))))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(AuthorDTO.class);
List<Map<String, Object>> results = executor.executeWith(em, strategy);

// Result: 50 authors × 20 books = 1000 book records (instead of 50,000+ without pagination)
```

### Advanced Syntax Examples

**Multi-field projections with shared prefix:**

```java
// Instead of:
.projection("books.id", "books.title", "books.year", "books.author")

// Use compact syntax:
.projection("books[size=10].id,title,year,author")
```

**Page navigation:**

```java
// First page (0-9)
.projection("books[size=10,page=0].id,title")

// Second page (10-19)
.projection("books[size=10,page=1].id,title")

// Third page (20-29)
.projection("books[size=10,page=2].id,title")
```

### Nested Collection Filtering

**Filter parent entities by collection properties:**

```java
// Find authors with books published after 2020
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("recentBooks", AuthorPropertyRef.BOOKS_YEAR, Op.GT, 2020)
    .combineWith("recentBooks")
    .projection("id", "name", "books[sort=year:desc].title,year")
    .build();
```

**PropertyReference for nested collections:**

```java
public enum AuthorPropertyRef implements PropertyReference {
    NAME,
    BOOKS_YEAR,  // Navigates to books.year for filtering
    BOOKS_TITLE;
    
    // Implementation maps BOOKS_YEAR -> "books.year"
}
```

---

## Maven Dependency

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-adapter-jpa</artifactId>
    <version>2.0.0</version>
</dependency>
```

---

## Next Steps

**FilterQL Modules:**
- **[Core Module](../../core/java/README.md)**: Framework-agnostic filtering infrastructure
- **[Spring Integration](../filterql-spring/README.md)**: Spring Boot integration with annotation processor
- **[Spring Starter](../filterql-spring-starter/README.md)**: Dependency aggregator for Spring Boot projects
- **[Main README](../../../README.md)**: Project overview

**External Dependencies (for DTO projections):**
- [Projection Specification](https://github.com/cyfko/projection-spec) - Annotation specification
- [Projection Metamodel Processor](https://github.com/cyfko/jpa-metamodel-processor) - Processor implementation
- [Maven Central: projection-metamodel-processor](https://search.maven.org/artifact/io.github.cyfko/jpa-metamodel-processor)

---

## License

MIT License - See [LICENSE](../../../LICENSE) file for details.

---

## Author

**Frank KOSSI** - frank.kossi@kunrin.com  
© 2024 Kunrin SA

