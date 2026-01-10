---
sidebar_position: 1
---

# Core Reference

Complete reference documentation for the `filterql-core` module (version 4.0.0).

---

## Maven Coordinates

```xml
<dependency>
    <groupId>io.github.cyfko</groupId>
    <artifactId>filterql-core</artifactId>
    <version>4.0.0</version>
</dependency>
```

---

## Package Structure

```
io.github.cyfko.filterql.core
├── api/                        # Core abstractions
│   ├── Condition              # Composable conditions
│   ├── DslParser              # DSL parsing contract
│   ├── FilterContext          # Backend bridge interface
│   └── FilterTree             # AST representation
├── spi/                        # Service Provider Interfaces
│   ├── FilterQuery            # Lifecycle facade
│   ├── PredicateResolver      # Deferred predicate generator
│   ├── ExecutionStrategy      # Execution strategy contract
│   └── QueryExecutor          # Execution coordinator
├── model/                      # Immutable data structures
│   ├── FilterRequest          # Request container
│   ├── FilterDefinition       # Atomic filter specification
│   ├── Pagination             # Pagination metadata
│   ├── SortBy                 # Sort specification
│   └── QueryExecutionParams   # Execution parameters
├── validation/                 # Type safety & validation
│   ├── PropertyReference      # Enum-based property refs
│   └── Op                     # Standard operators
├── exception/                  # Exception hierarchy
│   ├── DSLSyntaxException     # DSL parsing errors
│   ├── FilterValidationException # Validation failures
│   └── FilterDefinitionException # Definition errors
└── FilterQueryFactory          # Main facade
```

---

## Main API

### FilterQueryFactory

Main entry point for creating `FilterQuery` instances.

```java
package io.github.cyfko.filterql.core;

public class FilterQueryFactory {
    
    /**
     * Creates a FilterQuery with the default DSL parser.
     * 
     * @param context The context for condition resolution
     * @return new FilterQuery instance
     */
    public static <E> FilterQuery<E> of(FilterContext context);
    
    /**
     * Creates a FilterQuery with custom parser and context.
     * 
     * @param context The context for condition resolution
     * @param dslParser The parser for DSL expressions
     * @return new FilterQuery instance
     */
    public static <E> FilterQuery<E> of(FilterContext context, DslParser dslParser);
    
    /**
     * Creates a FilterQuery with projection policy.
     * 
     * @param context The context for condition resolution
     * @param dslParser The parser for DSL expressions
     * @param policy The projection policy for execution
     * @return new FilterQuery instance
     */
    public static <E> FilterQuery<E> of(
        FilterContext context, 
        DslParser dslParser, 
        ProjectionPolicy policy
    );
}
```

#### Usage Example

```java
// With default parser
FilterQuery<User> query = FilterQueryFactory.of(context);

// With custom parser
FilterQuery<User> query = FilterQueryFactory.of(context, new CustomDslParser());

// With projection policy
FilterQuery<User> query = FilterQueryFactory.of(
    context, 
    new BasicDslParser(), 
    ProjectionPolicy.defaults()
);
```

---

### FilterContext

Bridge interface between filter definitions and backend execution.

```java
package io.github.cyfko.filterql.core.api;

public interface FilterContext {
    
    /**
     * Transforms a FilterDefinition into a Condition.
     * 
     * @param argKey key to retrieve value from argument registry
     * @param ref the property reference
     * @param op the operator applied (case-insensitive)
     * @return the created Condition
     * @throws FilterDefinitionException if translation fails
     */
    <P extends Enum<P> & PropertyReference> Condition toCondition(
        String argKey, 
        P ref, 
        String op
    ) throws FilterDefinitionException;
    
    /**
     * Converts a Condition tree into an executable PredicateResolver.
     * 
     * @param condition the logical condition tree
     * @param params execution parameters (arguments + optional projection)
     * @return a PredicateResolver for generating backend predicates
     * @throws FilterDefinitionException if transformation fails
     */
    PredicateResolver<?> toResolver(
        Condition condition, 
        QueryExecutionParams params
    ) throws FilterDefinitionException;
}
```

---

### Condition

Composable representation of a filter condition.

```java
package io.github.cyfko.filterql.core.api;

public interface Condition {
    
    /**
     * Combines this condition with another using logical AND.
     * 
     * @param other the other condition
     * @return new composite condition
     */
    Condition and(Condition other);
    
    /**
     * Combines this condition with another using logical OR.
     * 
     * @param other the other condition
     * @return new composite condition
     */
    Condition or(Condition other);
    
    /**
     * Creates a negation of this condition.
     * 
     * @return negated condition
     */
    Condition negate();
    
    /**
     * Returns the associated predicate resolver.
     * 
     * @return the underlying PredicateResolver
     */
    PredicateResolver<?> getResolver();
}
```

---

### FilterQuery (SPI)

Filter query lifecycle interface.

```java
package io.github.cyfko.filterql.core.spi;

public interface FilterQuery<E> {
    
    /**
     * Resolves a FilterRequest into a PredicateResolver.
     * 
     * @param request the complete filter request
     * @return predicate resolver ready for execution
     */
    <P extends Enum<P> & PropertyReference> PredicateResolver<E> toResolver(
        FilterRequest<P> request
    );
    
    /**
     * Creates a QueryExecutor for the request.
     * 
     * @param request the complete filter request
     * @return executor configured for the strategy
     */
    <P extends Enum<P> & PropertyReference> QueryExecutor<E> toExecutor(
        FilterRequest<P> request
    );
}
```

---

### PredicateResolver (SPI)

Deferred predicate generator for JPA Criteria API.

```java
package io.github.cyfko.filterql.core.spi;

import jakarta.persistence.criteria.*;

@FunctionalInterface
public interface PredicateResolver<E> {
    
    /**
     * Resolves a JPA predicate from query components.
     * 
     * @param root the entity root
     * @param query the criteria query
     * @param cb the criteria builder
     * @return the resolved JPA predicate
     */
    Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb);
}
```

---

### QueryExecutor (SPI)

Query execution coordinator with generic context support.

```java
package io.github.cyfko.filterql.core.spi;

public interface QueryExecutor<Result> {
    
    /**
     * Executes with the specified strategy.
     * 
     * @param <Context> the type of execution context (e.g., EntityManager for JPA)
     * @param ctx the execution context
     * @param strategy the execution strategy
     * @return query results
     */
    <Context> Result executeWith(Context ctx, ExecutionStrategy<Result> strategy);
}
```

---

### ExecutionStrategy (SPI)

Execution strategy contract with generic context support.

```java
package io.github.cyfko.filterql.core.spi;

@FunctionalInterface
public interface ExecutionStrategy<R> {
    
    /**
     * Executes the FilterQL query using this strategy's specific execution logic.
     * 
     * @param <Context> the type of execution context (e.g., EntityManager for JPA)
     * @param ctx the execution context
     * @param pr the PredicateResolver for building filter predicates
     * @param params execution parameters (projection, pagination, sorting)
     * @return Result of type R
     */
    <Context> R execute(Context ctx, PredicateResolver<?> pr, QueryExecutionParams params);
}
```

---

## Models

### FilterRequest

Immutable container for a complete filter request.

```java
package io.github.cyfko.filterql.core.model;

public record FilterRequest<P extends Enum<P> & PropertyReference>(
    Map<String, FilterDefinition<P>> filters,  // Filter definitions
    String combineWith,                         // Boolean DSL expression
    Set<String> projection,                     // Optional DTO fields
    Pagination pagination                       // Pagination configuration
) {
    
    /** Checks if filters are present */
    public boolean hasFilters();
    
    /** Checks if a projection is requested */
    public boolean hasProjection();
    
    /** Checks if pagination is configured */
    public boolean hasPagination();
    
    /** Fluent builder */
    public static <R extends Enum<R> & PropertyReference> Builder<R> builder();
}
```

#### Builder

```java
public static class Builder<P extends Enum<P> & PropertyReference> {
    
    /** Adds a named filter definition */
    public Builder<P> filter(String name, FilterDefinition<P> definition);
    
    /** Adds a filter with string operator */
    public Builder<P> filter(String name, P ref, String op, Object value);
    
    /** Adds a filter with typed operator */
    public Builder<P> filter(String name, P ref, Op op, Object value);
    
    /** Adds multiple filters */
    public Builder<P> filters(Map<String, FilterDefinition<P>> filters);
    
    /** Sets the DSL expression */
    public Builder<P> combineWith(String expression);
    
    /** Sets the projection */
    public Builder<P> projection(Set<String> projection);
    public Builder<P> projection(String... fields);
    
    /** Configures pagination */
    public Builder<P> pagination(int pageNumber, int pageSize);
    public Builder<P> pagination(int pageNumber, int pageSize, 
                                 String sortField, String sortDirection,
                                 String... sortFields);
    
    /** Builds the immutable request */
    public FilterRequest<P> build();
}
```

---

### FilterDefinition

Immutable atomic filter specification.

```java
package io.github.cyfko.filterql.core.model;

public record FilterDefinition<P extends Enum<P> & PropertyReference>(
    P ref,           // Property reference
    String op,       // Operator code (normalized to uppercase)
    Object value     // Filter value (nullable for unary)
) {
    
    // Alternative constructor with Op enum
    public FilterDefinition(P ref, Op op, Object value);
}
```

---

### Pagination

Pagination configuration.

```java
package io.github.cyfko.filterql.core.model;

public record Pagination(
    int page,          // Page number (1-indexed)
    int size,          // Page size
    List<SortBy> sort  // Sort criteria (nullable)
) { }
```

---

### SortBy

Sort specification.

```java
package io.github.cyfko.filterql.core.model;

public record SortBy(
    String field,      // Field name
    String direction   // "ASC" or "DESC"
) { }
```

---

### QueryExecutionParams

Query execution parameters.

```java
package io.github.cyfko.filterql.core.model;

public record QueryExecutionParams(
    Map<String, Object> arguments,  // Argument key → value
    Set<String> projection          // DTO field paths (nullable)
) {
    
    /** Creates params without projection */
    public static QueryExecutionParams of(Map<String, Object> arguments);
    
    /** Creates params with projection */
    public static QueryExecutionParams withProjection(
        Map<String, Object> arguments, 
        Set<String> projection
    );
}
```

---

## Validation

### Op

Enum of the 14 standard operators.

```java
package io.github.cyfko.filterql.core.validation;

public enum Op {
    EQ("=", "EQ"),
    NE("!=", "NE"),
    GT(">", "GT"),
    GTE(">=", "GTE"),
    LT("<", "LT"),
    LTE("<=", "LTE"),
    MATCHES("LIKE", "MATCHES"),
    NOT_MATCHES("NOT LIKE", "NOT_MATCHES"),
    IN("IN", "IN"),
    NOT_IN("NOT IN", "NOT_IN"),
    IS_NULL("IS NULL", "IS_NULL"),
    NOT_NULL("IS NOT NULL", "NOT_NULL"),
    RANGE("BETWEEN", "RANGE"),
    NOT_RANGE("NOT BETWEEN", "NOT_RANGE"),
    CUSTOM(null, null);
    
    /** Parses an operator from symbol or code */
    public static Op fromString(String value);
    
    /** Checks if the operator requires a value */
    public boolean requiresValue();
    
    /** Checks if the operator supports multiple values */
    public boolean supportsMultipleValues();
}
```

---

### PropertyReference

Interface for type-safe property references.

```java
package io.github.cyfko.filterql.core.validation;

public interface PropertyReference {
    
    /** Java type of the property */
    Class<?> getType();
    
    /** Supported operators */
    Set<Op> getSupportedOperators();
    
    /** Target JPA entity type */
    Class<?> getEntityType();
}
```

---

## Exceptions

### DSLSyntaxException

```java
package io.github.cyfko.filterql.core.exception;

public class DSLSyntaxException extends RuntimeException {
    public DSLSyntaxException(String message);
    public DSLSyntaxException(String message, Throwable cause);
}
```

Thrown for:
- Empty or whitespace-only expression
- Unbalanced parentheses
- Invalid operators
- Undefined filter references

### FilterValidationException

```java
package io.github.cyfko.filterql.core.exception;

public class FilterValidationException extends RuntimeException {
    public FilterValidationException(String message);
}
```

Thrown for:
- Operator-type incompatibility
- Invalid value for operator
- Unsupported property

### FilterDefinitionException

```java
package io.github.cyfko.filterql.core.exception;

public class FilterDefinitionException extends RuntimeException {
    public FilterDefinitionException(String message);
}
```

Thrown for:
- Null property reference
- Null or empty operator code

---

## Next Steps

- [JPA Adapter Reference](jpa-adapter) - JpaFilterContext and execution strategies
- [Spring Adapter Reference](spring-adapter) - Spring annotations and services
- [Custom Operators Guide](../guides/custom-operators) - Implementing custom operators via PredicateResolverMapping
