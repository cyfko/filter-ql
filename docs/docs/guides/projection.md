---
sidebar_position: 4
---

# Projection

Projection lets you select only the fields you need, optimizing performance and data transfer.

---

## Basic Syntax

Projection fields are specified in the `FilterRequest`:

```java
FilterRequest<UserPropertyRef> request = FilterRequest.<UserPropertyRef>builder()
    .filter("activeFilter", UserPropertyRef.STATUS, Op.EQ, UserStatus.ACTIVE)
    .combineWith("activeFilter")
    .projection("id", "username", "email")  // Projected fields
    .build();
```

---

## Formal Grammar (EBNF)

```ebnf
(* Projection field specification *)
projection-field     = simple-field | nested-field | collection-field ;

(* Simple field without hierarchy *)
simple-field         = field-name ;

(* Nested field with dot notation *)
nested-field         = field-path , "." , field-list ;

(* Collection field with optional pagination/sorting *)
collection-field     = field-path-with-options , "." , field-list ;

(* Field path (may include collection options) *)
field-path           = field-segment , { "." , field-segment } ;
field-segment-with-options = field-name , [ collection-options ] ;

(* Multi-field list (comma-separated after last dot) *)
field-list           = field-name , { "," , field-name } ;

(* Collection pagination/sorting options *)
collection-options   = "[" , option-list , "]" ;
option-list          = option , { "," , option } ;
option               = size-option | page-option | sort-option ;

(* Individual options *)
size-option          = "size=" , positive-integer ;
page-option          = "page=" , non-negative-integer ;
sort-option          = "sort=" , sort-spec , { "," , sort-spec } ;
sort-spec            = field-name , ":" , sort-direction ;
sort-direction       = "asc" | "desc" | "ASC" | "DESC" ;
```

---

## Projection Types

### Simple Fields

```java
.projection("id", "username", "email", "age")
```

### Nested Fields

For JPA relations (`@ManyToOne`, `@OneToOne`), use dot notation:

```java
.projection(
    "id",
    "username",
    "address.city",        // Accesses user.address.city
    "address.country",     // Accesses user.address.country
    "department.name"      // Accesses user.department.name
)
```

### Compact Multi-Field Syntax

For multiple fields sharing the same prefix:

```java
// Compact syntax
.projection("id", "address.city,country,postalCode")

// Equivalent to:
.projection("id", "address.city", "address.country", "address.postalCode")
```

---

## Collection Projection

FilterQL supports collection projection (`@OneToMany`, `@ManyToMany`) with inline pagination and sorting.

### Available Options

| Option           | Description             | Default | Range      |
| ---------------- | ----------------------- | ------- | ---------- |
| `size=N`         | Page size               | 10      | 1 to 10000 |
| `page=P`         | Page number (0-indexed) | 0       | 0+         |
| `sort=field:dir` | Sort by field           | -       | asc/desc   |

### Examples

#### Simple Pagination

```java
// First 10 books per author
.projection("id", "name", "books[size=10].title,year")
```

#### With Sorting

```java
// 20 most recent books
.projection("id", "name", "books[size=20,sort=year:desc].title,year")
```

#### Combined Pagination and Sorting

```java
// Page 2 (items 20-39), sorted by year descending
.projection("id", "name", "books[size=20,page=1,sort=year:desc].title,author,year")
```

#### Multi-Column Sorting

```java
// Sort by year desc, then by title asc
.projection("id", "books[sort=year:desc,title:asc].title,year")
```

#### Hierarchical Pagination

```java
// 10 authors per entity, 5 books per author
.projection(
    "id",
    "name",
    "authors[size=10].name,books[size=5,sort=year:desc].title,year"
)
```

---

## Entities and DTOs

### Entity Definition

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

### DTO Definition with @Projection

:::note External Dependency
The `@Projection` annotation comes from [projection-spec](https://github.com/cyfko/projection-spec), implemented by [jpa-metamodel-processor](https://github.com/cyfko/jpa-metamodel-processor).
:::

```java
import io.github.cyfko.projection.Projection;
import io.github.cyfko.projection.Projected;

@Projection(from = Author.class)
public interface AuthorDTO {
    Long getId();
    String getName();

    @Projected(from = "books")
    List<BookSummaryDTO> getBooks();

    @Projected(from = "awards")
    Set<AwardDTO> getAwards();
}

@Projection(from = Book.class)
public interface BookSummaryDTO {
    Long getId();
    String getTitle();
    Integer getYear();
}
```

---

## Execution

### Without Collection Pagination

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

### With Collection Pagination

```java
FilterRequest<AuthorPropertyRef> request = FilterRequest.<AuthorPropertyRef>builder()
    .filter("nameFilter", AuthorPropertyRef.NAME, Op.MATCHES, "%smith%")
    .combineWith("nameFilter")
    .projection(
        "id",
        "name",
        "books[size=5,sort=year:desc].title,year"  // Last 5 books
    )
    .pagination(new Pagination(0, 20, null))
    .build();

MultiQueryFetchStrategy strategy = new MultiQueryFetchStrategy(AuthorDTO.class);
List<Map<String, Object>> results = executor.executeWith(em, strategy);
```

### JSON Result

```json
{
  "data": [
    {
      "id": 1,
      "name": "John Smith",
      "books": [
        { "title": "Latest Book", "year": 2024 },
        { "title": "Previous Work", "year": 2023 },
        { "title": "Classic Novel", "year": 2022 },
        { "title": "Early Writing", "year": 2021 },
        { "title": "First Book", "year": 2020 }
      ]
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## Validation Rules

### Consistent Options

Multiple references to the same collection must use identical options:

```java
// ❌ INVALID: conflicting options for 'books'
.projection(
    "books[size=10].title",
    "books[size=20].author"  // ERROR: different size
)

// ✅ VALID: identical options
.projection(
    "books[size=10,sort=year:desc].title",
    "books[size=10,sort=year:desc].author"
)

// ✅ VALID: use multi-field syntax
.projection(
    "books[size=10,sort=year:desc].title,author"
)
```

### Size Limits

```java
// ❌ INVALID: size > 10000
.projection("books[size=50000].title")  // ERROR

// ❌ INVALID: size < 1
.projection("books[size=0].title")  // ERROR
```

---

## Performance Considerations

| Aspect             | Recommendation                                             |
| ------------------ | ---------------------------------------------------------- |
| **Default fetch**  | Without options, collections are retrieved entirely        |
| **Batch fetching** | `MultiQueryFetchStrategy` uses batch fetching to avoid N+1 |
| **Memory**         | Pagination reduces memory footprint for large collections  |
| **Indexes**        | Sort fields SHOULD be indexed for optimal performance      |

---

## Next Steps

- [Collection Aggregations](../advanced-guide#reducers) - Calculate totals, averages, counts on nested collections
- [Custom Operators](custom-operators) - Create business operators
- [JPA Adapter Reference](../reference/jpa-adapter) - Detailed execution strategies
