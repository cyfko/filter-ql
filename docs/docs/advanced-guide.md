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

FilterQL allows adding custom operators via `PredicateResolverMapping` in the JPA adapter.

### Use Cases

- Phonetic search (Soundex, Metaphone)
- Full-text search
- Geographic operators (distance, within)
- JSON operators

### Implementation

**1. Define the custom mapping in JpaFilterContext:**

```java
JpaFilterContext<UserPropertyRef> context = new JpaFilterContext<>(
    UserPropertyRef.class,
    ref -> switch (ref) {
        case NAME -> new PredicateResolverMapping<User>() {
            @Override
            public PredicateResolver<User> map(String op, Object[] args) {
                return (root, query, cb) -> {
                    if ("SOUNDEX".equals(op)) {
                        String searchValue = (String) args[0];
                        if (searchValue == null || searchValue.isBlank()) {
                            throw new IllegalArgumentException("SOUNDEX requires non-blank value");
                        }
                        return cb.equal(
                            cb.function("SOUNDEX", String.class, root.get("name")),
                            cb.function("SOUNDEX", String.class, cb.literal(searchValue))
                        );
                    }
                    // Default behavior for standard operators
                    return cb.equal(root.get("name"), args[0]);
                };
            }
        };
        case EMAIL -> "email";  // Simple path mapping
        // ...
    }
);
```

**2. Use in a request:**

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

## Virtual Fields {#virtual-fields}

Virtual fields are one of FilterQL's most powerful features. They allow you to define **filterable properties that don't directly map to entity fields**, enabling complex query logic through a simple API.

### Why Virtual Fields?

| Use Case | Example |
|----------|---------|
| **Calculated properties** | Filter by `fullName` (combines firstName + lastName) |
| **Semantic aliases** | `isActive` instead of complex status checks |
| **Business logic encapsulation** | `hasAccess` with role/permission verification |
| **Multi-field searches** | Search across multiple columns simultaneously |
| **Aggregations** | `orderCount > 10` based on subqueries |
| **Dynamic filtering** | `withinMyOrg` based on current user's context |

### Basic Syntax

```java
import io.github.cyfko.filterql.core.spi.PredicateResolver;

@ExposedAs(value = "FIELD_NAME", operators = {Op.MATCHES, Op.EQ})
public static PredicateResolver<Entity> methodName(String op, Object[] args) {
    return (root, query, cb) -> {
        // Custom predicate logic using JPA Criteria API
        return cb.equal(root.get("field"), args[0]);
    };
}
```

**Method requirements:**
- **Return type:** `PredicateResolver<E>` where `E` is the entity type
- **Parameters:** `(String op, Object[] args)` — the operator and filter arguments
- **Visibility:** `public static` (or instance method for Spring beans)

---

### Static Virtual Fields

Static methods are ideal for **pure predicate logic** that doesn't require external dependencies.

#### Example: Full Name Search

```java
@Projection(entity = Person.class)
@Exposure(value = "persons", basePath = "/api")
public class PersonDTO {

    @ExposedAs(value = "FIRST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String firstName;

    @ExposedAs(value = "LAST_NAME", operators = {Op.EQ, Op.MATCHES})
    private String lastName;

    /**
     * Virtual field: searches in firstName OR lastName.
     */
    @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
    public static PredicateResolver<Person> fullNameMatches(String op, Object[] args) {
        return (root, query, cb) -> {
            if (args.length == 0) return cb.conjunction();
            String pattern = "%" + args[0] + "%";
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

#### Example: Admin User Filter

```java
/**
 * Virtual field defined in a dedicated resolver class.
 */
public class VirtualResolverConfig {

    @ExposedAs(value = "IS_ADMIN", operators = {Op.EQ})
    public static PredicateResolver<Person> isAdminUser(String op, Object[] args) {
        return (root, query, cb) -> {
            Boolean isAdmin = args.length > 0 ? (Boolean) args[0] : false;
            if (Boolean.TRUE.equals(isAdmin)) {
                return cb.equal(root.get("role"), "ADMIN");
            } else {
                return cb.notEqual(root.get("role"), "ADMIN");
            }
        };
    }
}

// Register as provider in the DTO
@Projection(
    entity = Person.class,
    providers = @Provider(VirtualResolverConfig.class)
)
public class PersonDTO { /* ... */ }
```

**Usage:**
```json
{
  "filters": {
    "admins": { "ref": "IS_ADMIN", "op": "EQ", "value": true }
  },
  "combineWith": "admins"
}
```

---

### Instance Virtual Fields (Spring Beans)

Instance methods are powerful for **context-aware filtering** that requires Spring services, security context, or other injected dependencies.

#### Example: Multi-Tenant Filtering

```java
@Component
public class UserTenancyService {

    @Autowired
    private SecurityContext securityContext;

    /**
     * Virtual field: filters users within the current user's organization.
     */
    @ExposedAs(value = "WITHIN_MY_ORG", operators = {Op.EQ})
    public PredicateResolver<Person> withinCurrentOrg(String op, Object[] args) {
        // Access current user from security context
        String currentUserOrg = securityContext.getCurrentUser().getOrganization();
        
        return (root, query, cb) -> {
            Boolean withinOrg = args.length > 0 ? (Boolean) args[0] : true;
            if (Boolean.TRUE.equals(withinOrg)) {
                return cb.equal(root.get("organizationId"), currentUserOrg);
            } else {
                return cb.notEqual(root.get("organizationId"), currentUserOrg);
            }
        };
    }
}

// Register as provider in the DTO
@Projection(
    entity = Person.class,
    providers = @Provider(UserTenancyService.class)
)
public class PersonDTO { /* ... */ }
```

**Usage:**
```json
{
  "filters": {
    "myOrg": { "ref": "WITHIN_MY_ORG", "op": "EQ", "value": true }
  },
  "combineWith": "myOrg"
}
```

#### Example: Role-Based Access Control

```java
@Component
public class AccessControlService {

    @Autowired
    private PermissionService permissions;

    /**
     * Filters resources the current user has access to.
     */
    @ExposedAs(value = "HAS_ACCESS", operators = {Op.EQ})
    public PredicateResolver<Document> hasAccess(String op, Object[] args) {
        List<Long> accessibleIds = permissions.getAccessibleResourceIds();
        
        return (root, query, cb) -> {
            Boolean hasAccess = args.length > 0 ? (Boolean) args[0] : true;
            if (Boolean.TRUE.equals(hasAccess)) {
                return root.get("id").in(accessibleIds);
            } else {
                return cb.not(root.get("id").in(accessibleIds));
            }
        };
    }
}
```

---

### Advanced Patterns

#### Using the Operator Argument

The `op` parameter gives you full control over operator-specific behavior:

```java
@ExposedAs(value = "COMPUTED_SCORE", operators = {Op.EQ, Op.GT, Op.LT, Op.GTE, Op.LTE})
public static PredicateResolver<Product> computedScore(String op, Object[] args) {
    return (root, query, cb) -> {
        // Calculate score as: rating * 10 + reviewCount
        var score = cb.sum(
            cb.prod(root.get("rating"), 10),
            root.get("reviewCount")
        );
        
        Integer threshold = (Integer) args[0];
        
        return switch (op) {
            case "EQ" -> cb.equal(score, threshold);
            case "GT" -> cb.gt(score, threshold);
            case "LT" -> cb.lt(score, threshold);
            case "GTE" -> cb.ge(score, threshold);
            case "LTE" -> cb.le(score, threshold);
            default -> cb.conjunction();
        };
    };
}
```

#### Subquery-Based Virtual Fields

```java
@ExposedAs(value = "ORDER_COUNT", operators = {Op.GT, Op.LT, Op.EQ})
public static PredicateResolver<Customer> orderCount(String op, Object[] args) {
    return (root, query, cb) -> {
        // Subquery to count orders
        var subquery = query.subquery(Long.class);
        var orderRoot = subquery.from(Order.class);
        subquery.select(cb.count(orderRoot))
               .where(cb.equal(orderRoot.get("customer"), root));
        
        Long count = ((Number) args[0]).longValue();
        
        return switch (op) {
            case "GT" -> cb.gt(subquery, count);
            case "LT" -> cb.lt(subquery, count);
            case "EQ" -> cb.equal(subquery, count);
            default -> cb.conjunction();
        };
    };
}
```

**Usage:**
```json
{
  "filters": {
    "bigCustomers": { "ref": "ORDER_COUNT", "op": "GT", "value": 10 }
  },
  "combineWith": "bigCustomers"
}
```

#### Combining Virtual and Regular Fields

Virtual fields work seamlessly with regular fields in filter expressions:

```json
{
  "filters": {
    "age": { "ref": "AGE", "op": "GT", "value": 18 },
    "name": { "ref": "FULL_NAME", "op": "MATCHES", "value": "John" },
    "myOrg": { "ref": "WITHIN_MY_ORG", "op": "EQ", "value": true }
  },
  "combineWith": "age & (name | myOrg)"
}
```

---

### Provider Registration

Virtual field methods can be defined in:

1. **The DTO class itself** (for tightly coupled logic)
2. **Dedicated resolver classes** (for reusable logic)
3. **Spring beans** (for context-aware logic)

Register them using `@Provider`:

```java
@Projection(
    entity = Person.class,
    providers = {
        @Provider(VirtualResolverConfig.class),  // Static methods
        @Provider(UserTenancyService.class)      // Spring bean (instance methods)
    }
)
@Exposure(value = "persons", basePath = "/api")
public class PersonDTO {
    // ...
}
```

---

### Best Practices

#### 1. Validate Arguments

Always check `args` before accessing:

```java
public static PredicateResolver<User> filter(String op, Object[] args) {
    return (root, query, cb) -> {
        if (args.length == 0 || args[0] == null) {
            return cb.conjunction();  // No filter applied
        }
        // Continue with filter logic...
    };
}
```

#### 2. Use Descriptive Names

```java
// ❌ Obscure
@ExposedAs(value = "F1", operators = {Op.EQ})

// ✅ Self-documenting
@ExposedAs(value = "IS_PREMIUM_CUSTOMER", operators = {Op.EQ})
```

#### 3. Limit Operators

Only expose operators that make sense for the virtual field:

```java
// ❌ Too permissive for a boolean-like field
@ExposedAs(value = "IS_ACTIVE", operators = {Op.EQ, Op.GT, Op.LT, Op.MATCHES})

// ✅ Appropriate for boolean semantics
@ExposedAs(value = "IS_ACTIVE", operators = {Op.EQ})
```

#### 4. Document Complex Logic

```java
/**
 * Virtual field: Filters products by computed popularity score.
 * 
 * Score formula: (rating * 10) + (reviewCount * 2) + (salesCount / 100)
 * 
 * Operators:
 * - GT/GTE: Products with score above threshold
 * - LT/LTE: Products with score below threshold
 * - EQ: Products with exact score (rarely useful)
 */
@ExposedAs(value = "POPULARITY_SCORE", operators = {Op.GT, Op.GTE, Op.LT, Op.LTE})
public static PredicateResolver<Product> popularityScore(String op, Object[] args) {
    // ...
}
```

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
