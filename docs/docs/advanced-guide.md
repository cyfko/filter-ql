---
sidebar_position: 4
---

# Advanced Guide

Complex cases, customization, and advanced techniques for experienced users.

---

## Advanced Projections {#projections}

The `@Projected` and `@Computed` annotations provide fine-grained control over entity-to-DTO mapping.

### @Projected: Renaming or Mapping a Field

When the DTO field name differs from the entity property name:

```java
@Entity
public class Order {
    @Id private Long id;
    private String orderNumber;  // Name in entity
    
    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
}

@Projection(entity = Order.class)
@Exposure(value = "orders", basePath = "/api")
public class OrderDTO {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "orderNumber")  // Maps orderNumber → number
    @ExposedAs(value = "ORDER_NUMBER", operators = {Op.EQ, Op.MATCHES})
    private String number;

    @Projected(from = "items")  // Maps items → orderItems
    private List<OrderItemDTO> orderItems;

    // Getters...
}
```

**Use cases:**
- Rename properties for the API (`orderNumber` → `number`)
- Map collections to different names
- Access nested properties (`@Projected(from = "department.name")`)

### @Computed: Calculated Fields

For fields that don't exist in the entity but are calculated from other properties:

```java
@Projection(entity = User.class, providers = @Provider(UserUtils.class))
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @Projected(from = "id")
    private Long id;

    @Projected(from = "name")
    private String name;

    @Projected(from = "orders")
    private List<OrderDTO> orders;

    /**
     * Field calculated from id and name.
     * The calculation method is in UserUtils.
     */
    @Computed(dependsOn = {"id", "name"})
    private String keyIdentifier;

    /**
     * Complex calculated field returning an object.
     */
    @Computed(dependsOn = {"id"})
    private UserHistory lastHistory;

    // Getters...

    public static class UserHistory {
        private String year;
        private String[] comments;
        // ...
    }
}
```

**Provider class with calculation methods:**

```java
public class UserUtils {
    
    /**
     * Called automatically to calculate keyIdentifier.
     * Method name follows convention: get + FieldName
     */
    public static String getKeyIdentifier(Long id, String name) {
        return id + "-" + name;
    }

    /**
     * Called automatically to calculate lastHistory.
     */
    public static UserDTO.UserHistory getLastHistory(Long id) {
        // Logic to retrieve history...
        return new UserDTO.UserHistory("2024", new String[]{"Created", "Updated"});
    }
}
```

**Important rules:**
- `@Provider`: Declares the class containing calculation methods
- `dependsOn`: Lists entity fields required for calculation
- Method must be `public static` and named `get<FieldName>`
- Parameters correspond to fields listed in `dependsOn`

---

## JPA Relations {#relations}

FilterQL can filter on relation properties (one-to-one, many-to-one, etc.).

### Example: User → Address

**Entities:**

```java
@Entity
public class User {
    @Id private Long id;
    private String name;
    
    @ManyToOne
    private Address address;
}

@Entity
public class Address {
    @Id private Long id;
    private String city;
    private String country;
}
```

**DTO with relation:**

```java
@Projection(entity = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @ExposedAs(value = "NAME", operators = {Op.EQ, Op.MATCHES})
    private String name;

    private AddressDTO address;  // Nested DTO
}

@Projection(entity = Address.class)
public class AddressDTO {

    @ExposedAs(value = "CITY", operators = {Op.EQ, Op.MATCHES, Op.IN})
    private String city;

    @ExposedAs(value = "COUNTRY", operators = {Op.EQ, Op.IN})
    private String country;
}
```

**Request:**

```json
{
  "filters": {
    "inParis": { "ref": "CITY", "op": "EQ", "value": "Paris" }
  },
  "combineWith": "inParis",
  "projection": ["name", "address.city,country"]
}
```

### Custom Mapping with JpaFilterContext

If the naming convention isn't enough, use explicit mapping:

```java
@Bean
public JpaFilterContext<UserProperty> userFilterContext(EntityManager em) {
    return new JpaFilterContext<>(
        UserProperty.class,
        (root, prop) -> switch (prop) {
            case NAME -> root.get("name");
            case ADDRESS_CITY -> root.get("address").get("city");
            case ADDRESS_COUNTRY -> root.get("address").get("country");
        }
    );
}
}
```

---

## Collections with Inline Pagination {#collections}

Filter collection elements and paginate the result directly in the projection.

### Example: User → Books

**Entity:**

```java
@Entity
public class User {
    @Id private Long id;
    private String name;
    
    @OneToMany(mappedBy = "author")
    private List<Book> books;
}

@Entity
public class Book {
    @Id private Long id;
    private String title;
    private Integer year;
    
    @ManyToOne
    private User author;
}
```

### Collection Projection Syntax

```json
{
  "projection": [
    "name",
    "books[size=5,sort=year:desc].title,year"
  ]
}
```

**Breakdown:**
- `books`: Collection name
- `[size=5,sort=year:desc]`: Collection options
- `.title,year`: Fields to extract from each element

### Available Options

| Option | Description | Default |
|--------|-------------|---------|
| `size=N` | Maximum number of elements | 10 |
| `page=P` | Page (0-indexed) | 0 |
| `sort=field:dir` | Sort (asc/desc) | - |

### Examples

**Last 10 books, sorted by year descending:**
```json
{
  "projection": ["name", "books[size=10,sort=year:desc].title,year"]
}
```

**Multi-column sort:**
```json
{
  "projection": ["name", "books[sort=year:desc,title:asc].title,year"]
}
```

**Page 2 of books (elements 20-29):**
```json
{
  "projection": ["name", "books[page=2,size=10].title"]
}
```

### Result

```json
{
  "content": [
    {
      "name": "Victor Hugo",
      "books": [
        { "title": "Les Misérables", "year": 1862 },
        { "title": "Notre-Dame de Paris", "year": 1831 }
      ]
    }
  ]
}
```

---

## Custom Operators {#custom-operators}

FilterQL allows adding your own operators via SPI.

### Use Cases

- Phonetic search (Soundex, Metaphone)
- Full-text search
- Geographic operators (distance, within)
- JSON operators

### Implementation

**1. Create the provider:**

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
```

**2. Register the provider:**

```java
@Configuration
public class FilterConfig {
    
    @PostConstruct
    public void registerOperators() {
        OperatorProviderRegistry.register(new SoundexOperatorProvider());
    }
}
```

**3. Allow the operator in PropertyReference:**

```java
@Override
public Set<Op> getSupportedOperators() {
    return switch (this) {
        case NAME -> Set.of(Op.EQ, Op.MATCHES, Op.CUSTOM); // CUSTOM allows registered operators
        // ...
    };
}
```

**4. Use in a request:**

```json
{
  "filters": {
    "phonetic": { "ref": "NAME", "op": "SOUNDEX", "value": "Smith" }
  },
  "combineWith": "phonetic"
}
```

---

## Advanced JPA Mapping {#jpa-mapping}

### PredicateResolverMapping

For total control over JPA predicate generation:

```java
@Bean
public JpaFilterContext<ProductProperty> productContext(EntityManager em) {
    return new JpaFilterContext<>(
        ProductProperty.class,
        (root, prop) -> switch (prop) {
            // Simple mapping
            case NAME -> root.get("name");
            
            // Relation
            case CATEGORY_NAME -> root.get("category").get("name");
            
            // Calculation (watch performance)
            case TOTAL_STOCK -> root.get("warehouse1Stock")
                                    .add(root.get("warehouse2Stock"));
        }
    );
}
```

### Execution Strategies

FilterQL JPA offers several execution strategies:

| Strategy | Description | Usage |
|----------|-------------|-------|
| `MultiQueryFetchStrategy` | Separate query for count and data | **Recommended** - Optimal performance |
| `FullEntityFetchStrategy` | Loads full entities | When you need the entities |
| `CountStrategy` | Count only, no data | Statistics |

```java
// Explicit use of a strategy
FilterQuery<User> query = FilterQueryFactory.of(context);
PaginatedResult<User> result = query.execute(
    request, 
    new MultiQueryFetchStrategy<>(em, User.class)
);
```

---

## Advanced Spring Configuration

### Customizing the Base Path

```java
@Projection(entity = User.class)
@Exposure(value = "users", basePath = "/api/v2")
public class UserDTO {
    // ...
}
```

Generates: `POST /api/v2/users/search`

### @ExposedAs Annotation

To customize how a property is exposed:

```java
@Projection(entity = User.class)
@Exposure(value = "users", basePath = "/api")
public class UserDTO {

    @ExposedAs(value = "USERNAME", operators = {Op.EQ, Op.MATCHES})
    private String name;
    
    @ExposedAs(exposed = false)  // Not exposed to API (not filterable)
    private String internalId;
    
    @ExposedAs(value = "EMAIL", operators = {Op.EQ, Op.MATCHES})
    private String email;
    
    private Integer age;  // Without @ExposedAs = not filterable but returned in projection
}
```

### Virtual Field with Custom Predicate

You can create "virtual" fields that execute custom logic:

```java
@Projection(entity = Person.class)
@Exposure(value = "persons", basePath = "/api")
public class PersonDTO {

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String firstName;

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String lastName;

    /**
     * Virtual field: searches in firstName AND lastName.
     * The static method returns a PredicateResolverMapping.
     */
    @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
    public static PredicateResolverMapping<Person> fullNameMatches() {
        return (root, query, cb, params) -> {
            if (params.length == 0) return cb.conjunction();
            String pattern = "%" + params[0] + "%";
            return cb.or(
                cb.like(root.get("firstName"), pattern),
                cb.like(root.get("lastName"), pattern)
            );
        };
    }
}
```

**Usage:**
```json
{
  "filters": {
    "name": { "ref": "FULL_NAME", "op": "MATCHES", "value": "john" }
  },
  "combineWith": "name"
}
```

Searches for "john" in `firstName` OR `lastName`.

---

## Best Practices

### 1. Limit Operators

Only allow necessary operators:

```java
// ❌ Too permissive
return OperatorUtils.ALL_OPS;

// ✅ Restrictive and intentional
return Set.of(Op.EQ, Op.MATCHES);
```

### 2. Validate Types

The type returned by `getType()` is used for validation and conversion:

```java
// ❌ Returns Object (no validation)
return Object.class;

// ✅ Precise type
return LocalDateTime.class;
```

### 3. Document Properties

Use explicit names in your enum:

```java
// ❌ Obscure
F1, F2, F3

// ✅ Self-documenting
USER_EMAIL, CREATED_AT, IS_ACTIVE
```

### 4. Test Your PropertyReference

```java
@Test
void allPropertiesShouldHaveTypes() {
    for (UserProperty prop : UserProperty.values()) {
        assertNotNull(prop.getType());
        assertFalse(prop.getSupportedOperators().isEmpty());
    }
}
```

---

## Technical Reference

For complete implementation details:

| Topic | Documentation |
|-------|---------------|
| Core API (interfaces, classes) | [→ Core Reference](./reference/core) |
| JPA API (context, strategies) | [→ JPA Reference](./reference/jpa-adapter) |
| Spring API (annotations, services) | [→ Spring Reference](./reference/spring-adapter) |
| Formal protocol specification | [→ Protocol](./protocol) |
