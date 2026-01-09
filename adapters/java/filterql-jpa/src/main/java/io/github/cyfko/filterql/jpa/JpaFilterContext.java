package io.github.cyfko.filterql.jpa;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.config.FilterConfig;
import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.QueryExecutionParams;
import io.github.cyfko.filterql.core.spi.CustomOperatorProvider;
import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.utils.TypeConversionUtils;
import io.github.cyfko.filterql.core.utils.FilterConfigUtils;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;
import io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping;
import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
import jakarta.persistence.criteria.*;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * JPA Criteria API implementation of {@link FilterContext} that materializes FilterQL
 * definitions into type-safe {@link Predicate} instances.
 *
 * <p>
 * This context acts as the JPA-specific bridge between:
 * </p>
 * <ul>
 *   <li>Enum-based {@link PropertyReference} definitions</li>
 *   <li>Operator semantics defined by {@link Op} and {@link OperatorProviderRegistry}</li>
 *   <li>Type conversion rules encapsulated in {@link FilterConfig} and {@link TypeConversionUtils}</li>
 *   <li>Custom predicate implementations via {@link PredicateResolverMapping}</li>
 * </ul>
 *
 * <p>
 * The lifecycle is split into two main phases:
 * </p>
 * <ol>
 *   <li>{@link #toCondition(String, Enum, String)} builds a {@link Condition} bound to a property and operator but not yet to concrete values.</li>
 *   <li>{@link #toResolver(Condition, QueryExecutionParams)} injects argument values and returns a {@link PredicateResolver} for Criteria queries.</li>
 * </ol>
 *
 * @param <P> Property enum type implementing {@link PropertyReference}
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see FilterContext
 * @see PropertyReference
 * @see FilterConfig
 * @see TypeConversionUtils
 * @see PredicateResolverMapping
 */
public class JpaFilterContext<P extends Enum<P> & PropertyReference> implements FilterContext {

    /**
     * Global filter behavior configuration (null handling, enum matching, string case, etc.).
     */
    private final FilterConfig filterConfig;

    /**
     * Enum class representing the property reference model handled by this context.
     */
    private final Class<P> enumClass;

    /**
     * Thread-local registry of argument values keyed by argument name.
     *
     * <p>
     * Suppliers created in {@link #toCondition(String, Enum, String)} resolve their actual
     * argument values from this registry after {@link #toResolver(Condition, QueryExecutionParams)}
     * has populated it for the current thread.
     * </p>
     */
    private final ThreadLocal<Map<String, Object>> argumentsRegistry = ThreadLocal.withInitial(HashMap::new);

    /**
     * Mapping function translating a property reference into either:
     * <ul>
     *   <li>a JPA path ({@link String}), or</li>
     *   <li>a {@link PredicateResolverMapping} instance for custom logic</li>
     * </ul>
     */
    private Function<P, Object> mappingBuilder;

    /**
     * Returns the enum class used as property reference in this context.
     *
     * @return Enum {@link Class} corresponding to {@code P}
     */
    public Class<P> getPropertyRefClass() {
        return enumClass;
    }

    /**
     * Constructs a new {@link JpaFilterContext} using default {@link FilterConfig}.
     *
     * <p>
     * Uses {@link FilterConfig#builder()} defaults for null handling, enum matching,
     * and string normalization. The mapping function is responsible for mapping each
     * property enum to either a JPA path or a {@link PredicateResolverMapping}.
     * </p>
     *
     * @param enumClass      Property enum class (must implement {@link PropertyReference})
     * @param mappingBuilder Mapping function returning path or {@link PredicateResolverMapping}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public JpaFilterContext(Class<P> enumClass, Function<P, Object> mappingBuilder) {
        this(enumClass, mappingBuilder, FilterConfig.builder().build());
    }

    /**
     * Constructs a new FilterContext for the specified property reference type.
     * <p>
     * This constructor initializes the context with the required type information and
     * mapping strategy. The mapping function determines how property references are
     * translated into concrete filter implementations.
     * </p>
     *
     * <p><strong>Mapping Function Requirements:</strong></p>
     * <p>The mapping function must return one of the following types:</p>
     * <ul>
     *   <li><strong>String:</strong> Property path for direct JPA attribute access (e.g., "name", "address.city")</li>
     *   <li><strong>PredicateResolverMapping:</strong> Custom filter logic implementation</li>
     * </ul>
     *
     * <p><strong>Simple Property Path Examples:</strong></p>
     * <pre>{@code
     * Function<FilterDefinition<UserPropertyRef>, Object> simpleMapping = def -> switch (def.ref()) {
     *     case NAME -> "name";                     // Direct property access
     *     case EMAIL -> "email";                   // Direct property access
     *     case AGE -> "age";                       // Direct property access
     *     case CITY -> "address.city.name";        // Nested property navigation
     *     case ACTIVE -> "status.active";          // Nested boolean property
     * };
     * }</pre>
     *
     * <p><strong>Custom Mapping Examples:</strong></p>
     * <pre>{@code
     * Function<FilterDefinition<UserPropertyRef>, Object> complexMapping = def -> switch (def.ref()) {
     *     // Simple path mappings
     *     case NAME -> "name";
     *     case EMAIL -> "email";
     *
     *     // Custom full-name search logic
     *     case FULL_NAME -> new PredicateResolverMapping<User>() {
     *         @Override
     *         public Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Object[] params) {
     *             String searchTerm = (String) params[0];
     *             return cb.or(
     *                 cb.like(cb.lower(root.get("firstName")), "%" + searchTerm.toLowerCase() + "%"),
     *                 cb.like(cb.lower(root.get("lastName")), "%" + searchTerm.toLowerCase() + "%"),
     *                 cb.like(cb.lower(cb.concat(root.get("firstName"), root.get("lastName"))),
     *                          "%" + searchTerm.toLowerCase() + "%")
     *             );
     *         }
     *     };
     *
     *     // Age range calculation from birth date
     *     case AGE_RANGE -> new PredicateResolverMapping<User>() {
     *         @Override
     *         public Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Object[] params) {
     *             int minAge = (int) params[0];
     *             int maxAge = (int) params[1];
     *             LocalDate now = LocalDate.now();
     *             LocalDate maxBirthDate = now.minusYears(minAge);
     *             LocalDate minBirthDate = now.minusYears(maxAge + 1);
     *             return cb.between(root.get("birthDate"), minBirthDate, maxBirthDate);
     *         }
     *     };
     * };
     * }</pre>
     */
    public JpaFilterContext(
            Class<P> enumClass,
            Function<P, Object> mappingBuilder,
            FilterConfig filterConfig
    ) {
        this.enumClass = Objects.requireNonNull(enumClass, "enumClass cannot be null");
        this.mappingBuilder = Objects.requireNonNull(mappingBuilder, "mappingBuilder cannot be null");
        this.filterConfig = Objects.requireNonNull(filterConfig, "filterConfig cannot be null");
    }

    /**
     * Replaces the current mapping builder function and returns the previous one.
     *
     * <p>
     * Can be used to switch mapping strategies at runtime (for example, based on tenant,
     * profile, or feature flags). Existing {@link Condition} instances retain the mapping
     * they were built with.
     * </p>
     *
     * <p><strong>Thread-safety:</strong> Not thread-safe. If called concurrently with
     * query execution, external synchronization is required.</p>
     *
     * @param mappingBuilder New mapping function (must not be {@code null})
     * @return Previously configured mapping function (never {@code null})
     * @throws NullPointerException if {@code mappingBuilder} is {@code null}
     */
    public Function<P, Object> setMappingBuilder(Function<P, Object> mappingBuilder) {
        var prev = this.mappingBuilder;
        this.mappingBuilder = Objects.requireNonNull(mappingBuilder);
        return prev;
    }

    /**
     * Creates a {@link Condition} for the given argument key, property reference, and operator.
     *
     * <p>
     * Validates argument key, reference type compatibility, and operator support (including
     * presence of a {@link CustomOperatorProvider} for {@link Op#CUSTOM}). The created
     * {@link Condition} holds a {@link PredicateResolver} that retrieves its parameter
     * lazily from the thread-local arguments registry.
     * </p>
     *
     * @param argKey Argument key used to look up values in {@link QueryExecutionParams#arguments()}
     * @param ref    Property reference enum
     * @param op     Operator string (e.g. "EQ", "IN", "CUSTOM")
     * @param <Q>    Property enum type implementing {@link PropertyReference}
     * @return Condition bound to property and operator, but not yet to concrete values
     *
     * @throws IllegalArgumentException  if key is blank, ref is null, or type mismatches
     * @throws FilterDefinitionException if CUSTOM operator has no registered provider
     */
    @Override
    public <Q extends Enum<Q> & PropertyReference> Condition toCondition(String argKey, Q ref, String op) {
        if (argKey == null || argKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Filter argument key cannot be null or empty");
        }
        if (ref == null) {
            throw new IllegalArgumentException("Filter definition cannot be null");
        }
        if (!enumClass.equals(ref.getClass())) {
            throw new IllegalArgumentException(String.format(
                    "Expected reference of type: %s, Found: %s",
                    enumClass,
                    ref.getClass()
            ));
        }
        if (Op.fromString(op) == Op.CUSTOM && OperatorProviderRegistry.getProvider(op).isEmpty()) {
            throw new FilterDefinitionException("Unknown operator : " + op);
        }
        if (!enumClass.isAssignableFrom(ref.getClass())) {
            throw new IllegalArgumentException(String.format(
                    "Provided definition is for Enum %s. Expected definition for Enum: %s.",
                    ref.getClass().getSimpleName(),
                    enumClass.getSimpleName()
            ));
        }

        @SuppressWarnings("unchecked")
        Object mapping = mappingBuilder.apply((P) ref);

        // Supplier that will resolve the parameter from the thread-local registry
        Supplier<Object> param = () -> {
            Map<String, Object> registry = argumentsRegistry.get();
            if (!registry.containsKey(argKey)) {
                throw new IllegalStateException(
                        "Argument key '" + argKey + "' not found in argumentsRegistry. " +
                                "Ensure toResolver() is called with a registry containing all required argument keys."
                );
            }
            return registry.get(argKey);
        };

        if (mapping instanceof PredicateResolverMapping<?> prm) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            PredicateResolver<?> resolver = (root, query, cb) -> {
                Object[] paramArray = toParameterArray(param.get());
                return prm.resolve((Root) root, query, cb, paramArray);
            };
            return new JpaCondition<>(resolver);
        }

        if (mapping instanceof String pathName) {
            PredicateResolver<?> resolver = getResolverFromPath(
                    this.filterConfig,
                    pathName,
                    () -> new FilterDefinition<>(ref, op, param.get())
            );
            return new JpaCondition<>(resolver);
        }

        throw new IllegalArgumentException("Invalid mapping function: only String and PredicateResolverMapping are supported");
    }

    /**
     * Normalizes a raw parameter to an {@link Object} array.
     *
     * <p>
     * Collections and arrays are expanded into a new array; single values are wrapped.
     * </p>
     *
     * @param p Parameter value (may be null, collection, array, or scalar)
     * @return Non-null array of parameters
     */
    private static Object[] toParameterArray(Object p) {
        if (p == null) return new Object[0];

        if (p instanceof Collection) {
            return ((Collection<?>) p).toArray();
        } else if (p.getClass().isArray()) {
            int length = Array.getLength(p);
            Object[] paramArray = new Object[length];
            for (int i = 0; i < length; i++) {
                paramArray[i] = Array.get(p, i);
            }
            return paramArray;
        }

        return new Object[]{ p };
    }

    /**
     * Converts a {@link Condition} into a {@link PredicateResolver} bound to the given parameters.
     *
     * <p>
     * Loads {@link QueryExecutionParams#arguments()} into the thread-local registry and
     * extracts the underlying {@link PredicateResolver} from the condition (expected to be
     * a {@link JpaCondition}).
     * </p>
     *
     * @param condition Condition created by {@link #toCondition(String, Enum, String)}
     * @param params    Execution parameters containing argument values
     * @return PredicateResolver bound to current thread arguments
     *
     * @throws IllegalArgumentException if condition is {@code null}
     * @throws NullPointerException     if params is {@code null}
     */
    @Override
    public PredicateResolver<?> toResolver(Condition condition, QueryExecutionParams params) {
        if (condition == null) {
            throw new IllegalArgumentException("condition cannot be null");
        }
        if (params == null) {
            throw new NullPointerException("params cannot be null");
        }

        Map<String, Object> localRegistry = this.argumentsRegistry.get();
        localRegistry.clear();
        localRegistry.putAll(params.arguments());

        return extractPredicateResolver(condition);
    }

    /**
     * Extracts the underlying {@link PredicateResolver} from a {@link Condition}.
     *
     * @param condition Condition instance (must be {@link JpaCondition})
     * @return Resolver extracted from the condition
     * @throws IllegalStateException if the condition type is not {@link JpaCondition}
     */
    private PredicateResolver<?> extractPredicateResolver(Condition condition) {
        try {
            JpaCondition<?> jpaCondition = (JpaCondition<?>) condition;
            return jpaCondition.getResolver();
        } catch (ClassCastException e) {
            throw new IllegalStateException(
                    "Unable to convert Condition to PredicateResolver. " +
                            "Expected JpaCondition but got: " + condition.getClass().getSimpleName(),
                    e
            );
        }
    }

    /**
     * Builds a {@link PredicateResolver} from a JPA path and a deferred {@link FilterDefinition}.
     *
     * <p>
     * This method is the core conversion engine that:
     * </p>
     * <ul>
     *   <li>Resolves the JPA {@link Path} using {@link PathResolverUtils}</li>
     *   <li>Applies null-value policy via {@link FilterConfigUtils}</li>
     *   <li>Converts parameter values using {@link TypeConversionUtils}</li>
     *   <li>Creates operator-specific predicates using {@link CriteriaBuilder}</li>
     * </ul>
     *
     * @param filterConfig       Filter configuration
     * @param pathName           JPA path name (e.g. "name", "address.city.name")
     * @param definitionSupplier Supplier providing a {@link FilterDefinition} with raw value
     * @param <E>                Entity type
     * @param <P>                Property enum type
     * @return PredicateResolver that can be used in a Criteria query
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <E, P extends Enum<P> & PropertyReference> PredicateResolver<E> getResolverFromPath(
            FilterConfig filterConfig,
            String pathName,
            Supplier<FilterDefinition<P>> definitionSupplier) {

        return (Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            FilterDefinition<P> definition = definitionSupplier.get();

            definition = FilterConfigUtils.ensureNullValuePolicy(filterConfig, definition);
            if (definition == null) {
                return cb.conjunction();
            }

            Path<?> path = PathResolverUtils.resolvePath(root, pathName);
            Class<?> pathJavaType = path.getJavaType();

            Object convertedValue = convertValueForPath(definition, pathJavaType, filterConfig);

            return switch (definition.operator()) {
                case EQ -> buildOrPredicateIfCollection(path, convertedValue, cb::equal, cb);
                case NE -> buildAndPredicateIfCollection(path, convertedValue, cb::notEqual, cb);

                case GT -> buildOrPredicateIfCollection(
                        castToComparablePath(path),
                        convertedValue,
                        (Expression<Comparable> p, Object v) -> cb.greaterThan(p, (Comparable) castToComparable(v)),
                        cb
                );
                case GTE -> buildOrPredicateIfCollection(
                        castToComparablePath(path),
                        convertedValue,
                        (Expression<Comparable> p, Object v) -> cb.greaterThanOrEqualTo(p, (Comparable) castToComparable(v)),
                        cb
                );
                case LT -> buildOrPredicateIfCollection(
                        castToComparablePath(path),
                        convertedValue,
                        (Expression<Comparable> p, Object v) -> cb.lessThan(p, (Comparable) castToComparable(v)),
                        cb
                );
                case LTE -> buildOrPredicateIfCollection(
                        castToComparablePath(path),
                        convertedValue,
                        (Expression<Comparable> p, Object v) -> cb.lessThanOrEqualTo(p, (Comparable) castToComparable(v)),
                        cb
                );

                case MATCHES -> {
                    Expression<String> expPath = castToStringPath(path);
                    Object adjustedValue = TypeConversionUtils.applyStringCaseStrategy(
                            convertedValue, filterConfig.getStringCaseStrategy());
                    Expression<String> pathExpr = switch (filterConfig.getStringCaseStrategy()) {
                        case LOWER -> cb.lower(expPath);
                        case UPPER -> cb.upper(expPath);
                        default -> expPath;
                    };
                    yield buildOrPredicateIfCollection(
                            pathExpr, adjustedValue,
                            (Expression<String> p, Object v) -> cb.like(p, castToString(v)), cb);
                }

                case NOT_MATCHES -> {
                    Expression<String> expPath = castToStringPath(path);
                    Object adjustedValue = TypeConversionUtils.applyStringCaseStrategy(
                            convertedValue, filterConfig.getStringCaseStrategy());
                    Expression<String> pathExpr = switch (filterConfig.getStringCaseStrategy()) {
                        case LOWER -> cb.lower(expPath);
                        case UPPER -> cb.upper(expPath);
                        default -> expPath;
                    };
                    yield buildAndPredicateIfCollection(
                            pathExpr, adjustedValue,
                            (Expression<String> p, Object v) -> cb.notLike(p, castToString(v)), cb);
                }

                case IN -> path.in(castToCollection(convertedValue));
                case NOT_IN -> cb.not(path.in(castToCollection(convertedValue)));
                case IS_NULL -> cb.isNull(path);
                case NOT_NULL -> cb.isNotNull(path);

                case RANGE -> {
                    Collection<?> collection = castToCollection(convertedValue);
                    Object[] valuesToCompare = collection.toArray();
                    if (valuesToCompare.length != 2) {
                        throw new IllegalArgumentException("RANGE operator requires exactly 2 values");
                    }
                    yield buildBetweenPredicate(path, valuesToCompare[0], valuesToCompare[1], cb);
                }

                case NOT_RANGE -> {
                    Collection<?> collection = castToCollection(convertedValue);
                    Object[] valuesToCompare = collection.toArray();
                    if (valuesToCompare.length != 2) {
                        throw new IllegalArgumentException("NOT_RANGE operator requires exactly 2 values");
                    }
                    Predicate between = buildBetweenPredicate(path, valuesToCompare[0], valuesToCompare[1], cb);
                    yield cb.not(between);
                }

                case CUSTOM -> {
                    Optional<CustomOperatorProvider> provider = OperatorProviderRegistry.getProvider(definition.op());
                    if (provider.isPresent()) {
                        yield provider.get().toResolver(definition).resolve((Root) root, query, cb);
                    }
                    throw new IllegalStateException("No provider found for CUSTOM operator <" + definition.op() + ">");
                }
            };
        };
    }

    /**
     * Converts filter value to the appropriate Java type for the target path.
     *
     * <p>
     * Handles both single-value and collection-based operators, delegating to
     * {@link TypeConversionUtils} according to {@link FilterConfig} enum match mode.
     * </p>
     *
     * @param definition   Filter definition
     * @param targetType   Expected Java type for the path
     * @param filterConfig Filter configuration
     * @param <P>          Property enum type
     * @return Converted value or collection of converted values
     */
    private static <P extends Enum<P> & PropertyReference> Object convertValueForPath(
            FilterDefinition<P> definition, Class<?> targetType, FilterConfig filterConfig) {

        Object value = definition.value();
        if (value == null) {
            return null;
        }

        if (requiresCollection(definition.operator())) {
            return TypeConversionUtils.convertToTypedCollection(value, targetType, filterConfig.getEnumMatchMode());
        }

        if (value instanceof Collection || value.getClass().isArray()) {
            return TypeConversionUtils.convertToTypedCollection(value, targetType, filterConfig.getEnumMatchMode());
        }

        return TypeConversionUtils.convertValue(targetType, value, filterConfig.getEnumMatchMode());
    }

    /**
     * Indicates whether an operator requires a collection of values.
     *
     * @param operator Operator
     * @return {@code true} for IN, NOT_IN, RANGE, NOT_RANGE
     */
    private static boolean requiresCollection(Op operator) {
        return operator == Op.IN
                || operator == Op.NOT_IN
                || operator == Op.RANGE
                || operator == Op.NOT_RANGE;
    }

    /**
     * Builds an OR-composed predicate when value is a collection; otherwise a single predicate.
     *
     * @param expression       JPA expression for the path
     * @param value            Single value or collection
     * @param predicateBuilder Single-value predicate factory
     * @param cb               CriteriaBuilder
     * @param <T>              Expression type
     * @return Combined predicate
     */
    private static <T> Predicate buildOrPredicateIfCollection(
            Expression<T> expression,
            Object value,
            BiFunction<Expression<T>, Object, Predicate> predicateBuilder,
            CriteriaBuilder cb) {

        if (value instanceof Collection<?> values) {
            if (values.isEmpty()) {
                return cb.disjunction();
            }

            List<Predicate> predicates = values.stream()
                    .map(v -> predicateBuilder.apply(expression, v))
                    .toList();

            return cb.or(predicates.toArray(new Predicate[0]));
        }

        return predicateBuilder.apply(expression, value);
    }

    /**
     * Builds an AND-composed predicate when value is a collection; otherwise a single predicate.
     *
     * @param expression       JPA expression for the path
     * @param value            Single value or collection
     * @param predicateBuilder Single-value predicate factory
     * @param cb               CriteriaBuilder
     * @param <T>              Expression type
     * @return Combined predicate
     */
    private static <T> Predicate buildAndPredicateIfCollection(
            Expression<T> expression,
            Object value,
            BiFunction<Expression<T>, Object, Predicate> predicateBuilder,
            CriteriaBuilder cb) {

        if (value instanceof Collection<?> values) {
            if (values.isEmpty()) {
                return cb.conjunction();
            }

            List<Predicate> predicates = values.stream()
                    .map(v -> predicateBuilder.apply(expression, v))
                    .toList();

            return cb.and(predicates.toArray(new Predicate[0]));
        }

        return predicateBuilder.apply(expression, value);
    }

    // ========================================
    // JPA-Specific Safe Casting Methods
    // ========================================

    /**
     * Casts a generic {@link Path} to a {@link Comparable} path for comparison operations.
     *
     * @param path Path to cast
     * @param <T>  Comparable type
     * @return Casted path
     */
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<? super T>> Path<T> castToComparablePath(Path<?> path) {
        return (Path<T>) path;
    }

    /**
     * Casts a generic {@link Path} to a {@link String} path for LIKE operations.
     *
     * @param path Path to cast
     * @return String path
     */
    @SuppressWarnings("unchecked")
    private static Path<String> castToStringPath(Path<?> path) {
        return (Path<String>) path;
    }

    /**
     * Casts a value to {@link Comparable} for use in range predicates.
     *
     * @param value Value to cast
     * @return Comparable value
     * @throws IllegalArgumentException if value is not {@link Comparable}
     */
    private static <T extends Comparable<? super T>> Comparable<?> castToComparable(Object value) {
        if (value instanceof Comparable<?> cmp) {
            return cmp;
        }
        throw new IllegalArgumentException("Value is not Comparable: " + value);
    }

    /**
     * Converts a value to {@link String} for LIKE operations.
     *
     * @param value Value to convert
     * @return String representation
     */
    private static String castToString(Object value) {
        return value.toString();
    }

    /**
     * Casts a value to {@link Collection} for IN-based operators.
     *
     * @param value Value to cast
     * @return Collection view
     * @throws IllegalArgumentException if value is not a collection
     */
    private static Collection<?> castToCollection(Object value) {
        if (value instanceof Collection) {
            return (Collection<?>) value;
        }
        throw new IllegalArgumentException("Value is not a Collection: " + value);
    }

    /**
     * Builds a BETWEEN predicate for a comparable path.
     *
     * @param path  JPA path
     * @param start Lower bound
     * @param end   Upper bound
     * @param cb    CriteriaBuilder
     * @return BETWEEN predicate
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Predicate buildBetweenPredicate(Path<?> path, Object start, Object end, CriteriaBuilder cb) {
        Path<? extends Comparable> comparablePath = castToComparablePath(path);
        Comparable startValue = castToComparable(start);
        Comparable endValue = castToComparable(end);
        return cb.between((Expression) comparablePath, startValue, endValue);
    }
}


//package io.github.cyfko.filterql.jpa;
//
//import io.github.cyfko.filterql.core.api.Condition;
//import io.github.cyfko.filterql.core.api.FilterContext;
//import io.github.cyfko.filterql.core.config.FilterConfig;
//import io.github.cyfko.filterql.core.exception.FilterDefinitionException;
//import io.github.cyfko.filterql.core.model.FilterDefinition;
//import io.github.cyfko.filterql.core.model.QueryExecutionParams;
//import io.github.cyfko.filterql.core.spi.CustomOperatorProvider;
//import io.github.cyfko.filterql.core.spi.OperatorProviderRegistry;
//import io.github.cyfko.filterql.core.spi.PredicateResolver;
//import io.github.cyfko.filterql.core.utils.TypeConversionUtils;
//import io.github.cyfko.filterql.core.utils.FilterConfigUtils;
//import io.github.cyfko.filterql.core.validation.Op;
//import io.github.cyfko.filterql.core.validation.PropertyReference;
//import io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping;
//import io.github.cyfko.filterql.jpa.utils.PathResolverUtils;
//import jakarta.persistence.criteria.*;
//
//import java.lang.reflect.Array;
//import java.util.*;
//import java.util.function.BiFunction;
//import java.util.function.Function;
//import java.util.function.Supplier;
//
///**
// * JPA Criteria API implementation of {@link FilterContext} for dynamic query filtering.
// * <p>
// * This class provides a type-safe bridge between FilterQL filter definitions and JPA
// * Criteria API predicates. It handles the complete lifecycle of filter transformation:
// * from property references to database predicates, including type conversion, validation,
// * and custom business logic integration.
// * </p>
// *
// * <h2>Core Capabilities</h2>
// * <ul>
// *   <li><strong>Type Safety:</strong> Enum-based property references to prevent typos and enable IDE support</li>
// *   <li><strong>Automatic Type Conversion:</strong> Handles all JPA types via {@link TypeConversionUtils}:
// *     primitives, dates, enums, UUIDs, collections, and custom types</li>
// *   <li><strong>Flexible Mapping:</strong> Supports simple property paths and custom resolver logic</li>
// *   <li><strong>Collection Support:</strong> Automatic OR/AND logic for multi-value filters</li>
// *   <li><strong>Nested Properties:</strong> Dot notation for deep object graphs (e.g., "address.city.name")</li>
// *   <li><strong>Custom Operators:</strong> Extensible operator system via {@link CustomOperatorProvider}</li>
// *   <li><strong>Configurable Behavior:</strong> Fine-grained control through {@link FilterConfig}</li>
// * </ul>
// *
// * <h2>Architecture</h2>
// * <p>
// * The filtering process follows this flow:
// * </p>
// * <ol>
// *   <li>Client defines filters using enum-based property references</li>
// *   <li>{@link #toCondition(String, Enum, String)} validates and maps to conditions</li>
// *   <li>Mapping function returns either a path string or custom resolver</li>
// *   <li>{@link #toCondition(String, Enum, String)} assembles conditions into a final JPA predicate</li>
// *   <li>Type conversion and validation occur during predicate execution</li>
// * </ol>
// *
// * <h2>Mapping Strategies</h2>
// * <p>The mapping function can return two types of objects:</p>
// * <dl>
// *   <dt><strong>String (Property Path)</strong></dt>
// *   <dd>
// *     For standard property filtering. Supports nested paths with dot notation.
// *     The framework handles all type conversion and operator logic automatically.
// *     <pre>{@code case NAME -> "name"; }</pre>
// *   </dd>
// *
// *   <dt><strong>PredicateResolverMapping</strong></dt>
// *   <dd>
// *     For custom business logic requiring manual predicate construction.
// *     Useful for computed properties, multi-field searches, or complex conditions.
// *     <pre>{@code
// *     case AGE_RANGE -> new PredicateResolverMapping<User>() {
// *         @Override
// *         public Predicate resolve(Root<E> root, CriteriaQuery<?> q, CriteriaBuilder cb, Object[] params) {
// *             Integer min = (List<Integer>) params[0];
// *             Integer max = (List<Integer>) params[0];
// *             LocalDate maxBirth = LocalDate.now().minusYears(min);
// *             LocalDate minBirth = LocalDate.now().minusYears(max + 1);
// *             return cb.between(root.get("birthDate"), minBirth, maxBirth);
// *         }
// *     };
// *     }</pre>
// *   </dd>
// * </dl>
// *
// * <h2>Type Conversion</h2>
// * <p>
// * Type conversion is handled automatically by {@link TypeConversionUtils} and supports:
// * </p>
// * <ul>
// *   <li>Primitives and wrappers (int, Integer, long, Long, etc.)</li>
// *   <li>Numeric types (BigDecimal, BigInteger, Double, Float, etc.)</li>
// *   <li>Date/Time types (LocalDate, LocalDateTime, Instant, ZonedDateTime, etc.)</li>
// *   <li>Enums (case-sensitive and case-insensitive matching)</li>
// *   <li>UUIDs (from String)</li>
// *   <li>Collections (List, Set, arrays, CSV strings)</li>
// *   <li>Custom types via standard Java conversion methods</li>
// * </ul>
// *
// * <h2>Configuration Options</h2>
// * <p>Behavior customization via {@link FilterConfig}:</p>
// * <ul>
// *   <li><strong>NullValuePolicy:</strong> REJECT (throw error) or IGNORE (skip filter)</li>
// *   <li><strong>StringCaseStrategy:</strong> NONE, UPPER, LOWER for string normalization</li>
// *   <li><strong>EnumMatchMode:</strong> CASE_SENSITIVE or CASE_INSENSITIVE</li>
// * </ul>
// *
// * <h2>Thread Safety</h2>
// * <p>
// * <strong>Instance fields are immutable after construction.</strong> However, the class
// * uses a {@link ThreadLocal} registry for argument values during predicate resolution.
// * This makes individual instances thread-safe for concurrent filter execution, but
// * not for concurrent configuration changes.
// * </p>
// *
// * <h2>Error Handling</h2>
// * <p>The class validates inputs at multiple stages:</p>
// * <ul>
// *   <li><strong>Construction:</strong> All parameters must be non-null</li>
// *   <li><strong>Condition Creation:</strong> Validates operator support and enum type compatibility</li>
// *   <li><strong>Resolution:</strong> Validates argument presence and type compatibility</li>
// * </ul>
// * <p>
// * Invalid inputs throw:
// * {@link IllegalArgumentException}, {@link FilterDefinitionException}, or
// * {@link IllegalStateException} depending on the context.
// * </p>
// *
// * @param <P> The PropertyReference enum type defining filterable properties
// *
// * @author FilterQL Team
// * @since 2.0.0
// * @see FilterContext
// * @see PropertyReference
// * @see FilterConfig
// * @see TypeConversionUtils
// * @see PredicateResolverMapping
// */
//public class JpaFilterContext<P extends Enum<P> & PropertyReference> implements FilterContext {
//    private final FilterConfig filterConfig;
//    private final Class<P> enumClass;
//
//    /**
//     * Thread-local storage for deferred argument values.
//     * This ensures thread-safety when JpaFilterContext instances are shared across multiple requests.
//     * The argumentsRegistry is populated in toResolver() and accessed by Suppliers created in toCondition().
//     */
//    private final ThreadLocal<Map<String, Object>> argumentsRegistry = ThreadLocal.withInitial(HashMap::new);
//
//    private Function<P, Object> mappingBuilder;
//
//    /**
//     * Returns the enum class used as a property reference in filtering or querying.
//     * <p>
//     * This method provides the {@code Class} object representing the enum type {@code P}
//     * that defines the property references in the filtering framework. It facilitates
//     * generic handling of filters, dynamic criteria adjustments, and interpretation of
//     * enumerated values used in data operations.
//     * </p>
//     *
//     * @return the {@code Class} instance corresponding to the enum type {@code P}.
//     */
//    public Class<P> getPropertyRefClass() {
//        return enumClass;
//    }
//
//    /**
//     * Constructs a new FilterContext for the specified entity and property reference types without projection support.
//     * <p>
//     * This constructor initializes the context with the required type information and
//     * mapping strategy. The mapping function determines how property references are
//     * translated into concrete filter implementations. This constructor does NOT support projection.
//     * </p>
//     *
//     * <p><strong>Mapping Function Requirements:</strong></p>
//     * <p>The mapping function must return one of the following types:</p>
//     * <ul>
//     *   <li><strong>String:</strong> Property path for direct JPA attribute access (e.g., "name", "address.city")</li>
//     *   <li><strong>PredicateResolverMapping:</strong> Custom filter logic implementation</li>
//     * </ul>
//     *
//     * <p><strong>Simple Property Path Examples:</strong></p>
//     * <pre>{@code
//     * Function<FilterDefinition<UserPropertyRef>, Object> simpleMapping = def -> switch (def.ref()) {
//     *     case NAME -> "name";                     // Direct property access
//     *     case EMAIL -> "email";                   // Direct property access
//     *     case AGE -> "age";                       // Direct property access
//     *     case CITY -> "address.city.name";        // Nested property navigation
//     *     case ACTIVE -> "status.active";          // Nested boolean property
//     * };
//     * }</pre>
//     *
//     * <p><strong>Custom Mapping Examples:</strong></p>
//     * <pre>{@code
//     * Function<FilterDefinition<UserPropertyRef>, Object> complexMapping = def -> switch (def.ref()) {
//     *     // Simple path mappings
//     *     case NAME -> "name";
//     *     case EMAIL -> "email";
//     *
//     *     // Custom full-name search logic
//     *     case FULL_NAME -> new PredicateResolverMapping<User>() {
//     *         @Override
//     *         public Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Object[] params) {
//     *                 String searchTerm = (String) params[0];
//     *                 return cb.or(
//     *                     cb.like(cb.lower(root.get("firstName")), "%" + searchTerm.toLowerCase() + "%"),
//     *                     cb.like(cb.lower(root.get("lastName")), "%" + searchTerm.toLowerCase() + "%"),
//     *                     cb.like(cb.lower(cb.concat(root.get("firstName"), root.get("lastName"))),
//     *                              "%" + searchTerm.toLowerCase() + "%")
//     *                 );
//     *         }
//     *     };
//     *
//     *     // Age range calculation from birth date
//     *     case AGE_RANGE -> new PredicateResolverMapping<User>() {
//     *         @Override
//     *         public Predicate resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, Object[] params) {
//     *                 int minAge = (int) params[0];
//     *                 int maxAge = (int) params[1];
//     *                 LocalDate now = LocalDate.now();
//     *                 LocalDate maxBirthDate = now.minusYears(minAge);
//     *                 LocalDate minBirthDate = now.minusYears(maxAge + 1);
//     *                 return cb.between(root.get("birthDate"), minBirthDate, maxBirthDate);
//     *         }
//     *     };
//     * };
//     * }</pre>
//     *
//     * <p><strong>Validation and Error Handling:</strong></p>
//     * <p>The constructor validates that all parameters are non-null but does not
//     * validate the mapping function's behavior. Invalid mappings will be detected.
//     * </p>
//     *
//     * @param enumClass The class of the property reference enum (e.g., UserPropertyRef.class)
//     * @param mappingBuilder The function that maps filter definitions to filter implementations.
//     *                      Must return either a String (property path) or PredicateResolverMapping
//     * @throws NullPointerException if any parameter is null
//     * @see PredicateResolverMapping
//     * @see PropertyReference
//     */
//    public JpaFilterContext(Class<P> enumClass, Function<P, Object> mappingBuilder) {
//        this(enumClass, mappingBuilder, FilterConfig.builder().build());
//    }
//
//    /**
//     * Full constructor with configuration support (v2.0+).
//     *
//     * <h3>Usage Example</h3>
//     * <pre>{@code
//     * // Create mapping function
//     * BiFunction<UserProperty, String, Object> mapping = (prop, op) -> switch (prop) {
//     *     case NAME -> "name";
//     *     case EMAIL -> "email";
//     *     case CITY -> "address.city.name";
//     *     case FULL_NAME_SEARCH -> (PredicateResolverMapping<User>)
//     *         (root, query, cb, params) -> cb.or(
//     *             cb.like(root.get("firstName"), "%" + params[0] + "%"),
//     *             cb.like(root.get("lastName"), "%" + params[0] + "%")
//     *         );
//     * };
//     *
//     * JpaFilterContext<UserProperty> context = new JpaFilterContext<>(
//     *     UserProperty.class,
//     *     mapping,
//     *     FilterConfig.builder().stringCaseStrategy(StringCaseStrategy.LOWER).build()
//     * );
//     * }</pre>
//     *
//     * @param enumClass The PropertyReference enum class (must not be null)
//     * @param mappingBuilder Function mapping property references to paths or resolvers (must not be null)
//     * @param filterConfig Configuration object for filtering strategies (must not be null)
//     * @throws NullPointerException if enumClass, mappingBuilder, or filterConfig is null
//     * @since 2.0.0
//     * @see FilterConfig
//     * @see PredicateResolverMapping
//     */
//    public JpaFilterContext(
//        Class<P> enumClass,
//        Function<P, Object> mappingBuilder,
//        FilterConfig filterConfig
//    ) {
//        this.enumClass = Objects.requireNonNull(enumClass, "enumClass cannot be null");
//        this.mappingBuilder = Objects.requireNonNull(mappingBuilder, "mappingBuilder cannot be null");
//        this.filterConfig = Objects.requireNonNull(filterConfig, "filterConfig cannot be null");
//    }
//
//    /**
//     * Sets the mapping builder function for transforming property references.
//     * <p>
//     * This method allows dynamic reconfiguration of how property references are mapped
//     * to filter implementations. It's useful for scenarios where mapping logic needs
//     * to be updated at runtime, such as multi-tenant applications or A/B testing
//     * of different filtering strategies.
//     * </p>
//     *
//     * <p><strong>Thread Safety:</strong> This method is not thread-safe. If called
//     * concurrently with filter operations, it may cause inconsistent behavior.
//     * Ensure proper synchronization if runtime changes are required.</p>
//     *
//     * <p><strong>Usage Example:</strong></p>
//     * <pre>{@code
//     * // Switch to a more permissive mapping for admin users
//     * BiFunction<UserProperty, String, Object> adminMapping = (prop, op) -> switch (prop) {
//     *     case NAME -> "name";
//     *     case EMAIL -> "email";
//     *     case ADMIN_NOTES -> "internalNotes";  // Only available for admins
//     * };
//     *
//     * BiFunction<UserProperty, String, Object> oldMapping = context.setMappingBuilder(adminMapping);
//     *
//     * // Later, restore the previous mapping
//     * context.setMappingBuilder(oldMapping);
//     * }</pre>
//     *
//     * <p><strong>Important Notes:</strong></p>
//     * <ul>
//     *   <li>Affects all subsequent calls to {@link #toCondition(String, Enum, String)}</li>
//     *   <li>Previously created {@link Condition} objects use their original mappings</li>
//     *   <li>The previous builder is returned to enable restoration or fallback</li>
//     *   <li>Must not be null (enforced via {@link Objects#requireNonNull(Object)})</li>
//     * </ul>
//     *
//     * @param mappingBuilder the new function to transform property references to paths or resolvers, must not be null
//     * @return the previous mapping builder that was set, never null
//     * @throws NullPointerException if mappingBuilder is null
//     */
//    public Function<P, Object> setMappingBuilder(Function<P, Object> mappingBuilder) {
//        var prev = this.mappingBuilder;
//        this.mappingBuilder = Objects.requireNonNull(mappingBuilder);
//        return prev;
//    }
//
//    @Override
//    public <Q extends Enum<Q> & PropertyReference> Condition toCondition(String argKey, Q ref, String op) {
//        // Ensure constraints are met
//        if (argKey == null || argKey.trim().isEmpty()) {
//            throw new IllegalArgumentException("Filter argument key cannot be null or empty");
//        }
//        if (ref == null) {
//            throw new IllegalArgumentException("Filter definition cannot be null");
//        }
//        if (!enumClass.equals(ref.getClass())) {
//            throw new IllegalArgumentException(String.format(
//                    "Expected reference of type: %s, Found: %s",
//                    enumClass,
//                    ref.getClass()
//            ));
//        }
//        if (Op.fromString(op) == Op.CUSTOM && OperatorProviderRegistry.getProvider(op).isEmpty()) {
//            throw new FilterDefinitionException("Unknown operator : " + op);
//        }
//
//        // Ensure definition is for the same property reference enumeration
//        if (! enumClass.isAssignableFrom(ref.getClass())) {
//            throw new IllegalArgumentException(String.format(
//                    "Provided definition is for Enum %s. Expected definition for Enum: %s.",
//                    ref.getClass().getSimpleName(),
//                    enumClass.getSimpleName()
//            ));
//        }
//
//        // Transform definition into a filter condition using the mapping function
//        @SuppressWarnings("unchecked")
//        Object mapping = mappingBuilder.apply((P) ref);
//
//        // Create Supplier that retrieves value from thread-local registry when invoked
//        // The registry will be populated later in toResolver()
//        Supplier<Object> param = () -> {
//            Map<String, Object> registry = argumentsRegistry.get();
//            if (!registry.containsKey(argKey)) {
//                throw new IllegalStateException(
//                    "Argument key '" + argKey + "' not found in argumentsRegistry. " +
//                    "Ensure toResolver() is called with a registry containing all required argument keys."
//                );
//            }
//            return registry.get(argKey);
//        };
//
//        if (mapping instanceof PredicateResolverMapping<?> prm) {
//            @SuppressWarnings({"rawtypes", "unchecked"})
//            PredicateResolver<?> resolver = (root, query, cb) -> {
//                Object[] paramArray = toParameterArray(param.get());
//                return prm.resolve((Root) root, query, cb, paramArray);
//            };
//            return new JpaCondition<>(resolver);
//        }
//
//        if (mapping instanceof String pathName) {
//            PredicateResolver<?> resolver = getResolverFromPath(
//                    this.filterConfig,
//                    pathName,
//                    () -> new FilterDefinition<>(ref, op, param.get())
//            );
//            return new JpaCondition<>(resolver);
//        }
//
//        throw new IllegalArgumentException("Invalid mapping function unsupported yet");
//    }
//
//    private static Object[] toParameterArray(Object p) {
//        if (p == null) return new Object[0];
//
//        if (p instanceof Collection) {
//            return ((Collection<?>) p).toArray();
//        } else if (p.getClass().isArray()) {
//            int length = Array.getLength(p);
//            Object[] paramArray = new Object[length];
//            for (int i = 0; i < length; i++) {
//                paramArray[i] = Array.get(p, i);
//            }
//            return paramArray;
//        }
//
//        return new Object[]{ p };
//    }
//
//    @Override
//    public PredicateResolver<?> toResolver(Condition condition, QueryExecutionParams params) {
//        // Validate inputs
//        if (condition == null) {
//            throw new IllegalArgumentException("condition cannot be null");
//        }
//        if (params == null) {
//            throw new NullPointerException("params cannot be null");
//        }
//
//        // Populate the thread-local argumentsRegistry with provided values
//        // This ensures that the Suppliers created in toCondition() will retrieve the correct values
//        // Thread-safe: each thread has its own copy
//        Map<String, Object> localRegistry = this.argumentsRegistry.get();
//        localRegistry.clear();
//        localRegistry.putAll(params.arguments());
//
//        // Extract the base predicate resolver from condition
//        return extractPredicateResolver(condition);
//    }
//
//    /**
//     * Extracts PredicateResolver from Condition.
//     */
//    private PredicateResolver<?> extractPredicateResolver(Condition condition) {
//        try {
//            JpaCondition<?> jpaCondition = (JpaCondition<?>) condition;
//            return jpaCondition.getResolver();
//        } catch (ClassCastException e) {
//            throw new IllegalStateException(
//                "Unable to convert Condition to PredicateResolver. " +
//                "Expected JpaCondition but got: " + condition.getClass().getSimpleName(),
//                e
//            );
//        }
//    }
//
//    /**
//     * Builds a JPA PredicateResolver from a property path and filter definition.
//     * <p>
//     * This method is the core conversion engine that transforms FilterQL definitions into
//     * JPA Criteria API predicates. It handles type conversion, collection support, and
//     * operator-specific logic.
//     * </p>
//     *
//     * <p><strong>Key Features:</strong></p>
//     * <ul>
//     *   <li><strong>Automatic Type Conversion:</strong> Converts values to match the target property type</li>
//     *   <li><strong>Collection Support:</strong> Singleton operators (EQ, NE, GT, etc.) support collections with OR/AND logic</li>
//     *   <li><strong>Nested Path Resolution:</strong> Handles complex paths like "address.city.name"</li>
//     *   <li><strong>Null Safety:</strong> Proper handling of null values</li>
//     * </ul>
//     *
//     * <p><strong>Operator Behavior:</strong></p>
//     * <table border="1">
//     *   <tr>
//     *     <th>Operator</th>
//     *     <th>Single Value</th>
//     *     <th>Collection Value</th>
//     *   </tr>
//     *   <tr>
//     *     <td>EQ</td>
//     *     <td>field = value</td>
//     *     <td>field = v1 OR field = v2 OR field = v3</td>
//     *   </tr>
//     *   <tr>
//     *     <td>NE</td>
//     *     <td>field != value</td>
//     *     <td>field != v1 AND field != v2 AND field != v3</td>
//     *   </tr>
//     *   <tr>
//     *     <td>GT, GTE, LT, LTE</td>
//     *     <td>field > value</td>
//     *     <td>field > v1 OR field > v2</td>
//     *   </tr>
//     *   <tr>
//     *     <td>MATCHES</td>
//     *     <td>LOWER(field) LIKE LOWER(pattern)</td>
//     *     <td>LOWER(field) LIKE LOWER(p1) OR LOWER(field) LIKE LOWER(p2)</td>
//     *   </tr>
//     *   <tr>
//     *     <td>NOT_MATCHES</td>
//     *     <td>LOWER(field) NOT LIKE LOWER(pattern)</td>
//     *     <td>LOWER(field) NOT LIKE LOWER(p1) AND LOWER(field) NOT LIKE LOWER(p2)</td>
//     *   </tr>
//     *   <tr>
//     *     <td>IN</td>
//     *     <td>field IN (value)</td>
//     *     <td>field IN (v1, v2, v3)</td>
//     *   </tr>
//     *   <tr>
//     *     <td>RANGE</td>
//     *     <td>N/A (requires 2 values)</td>
//     *     <td>field BETWEEN v1 AND v2</td>
//     *   </tr>
//     * </table>
//     *
//     * <p><strong>Type Conversion Examples:</strong></p>
//     * <pre>{@code
//     * // String to Integer
//     * FilterDefinition(AGE, EQ, "25") → WHERE age = 25
//     *
//     * // String to LocalDate
//     * FilterDefinition(BIRTH_DATE, GT, "2000-01-01") → WHERE birthDate > '2000-01-01'
//     *
//     * // String to Enum (case-insensitive)
//     * FilterDefinition(STATUS, EQ, "active") → WHERE status = Status.ACTIVE
//     *
//     * // Case-insensitive LIKE
//     * FilterDefinition(NAME, MATCHES, "%john%") → WHERE LOWER(name) LIKE "%john%"
//     *
//     * // Long to Date
//     * FilterDefinition(CREATED_AT, LT, 1640000000000L) → WHERE createdAt < '2021-12-20...'
//     *
//     * // Number to Number (with precision conversion)
//     * FilterDefinition(PRICE, GTE, 99.99) → WHERE price >= 99.99
//     * }</pre>
//     *
//     * <p><strong>Collection Examples:</strong></p>
//     * <pre>{@code
//     * // Multiple status values with OR logic
//     * FilterDefinition(STATUS, EQ, Arrays.asList("ACTIVE", "PENDING"))
//     * → WHERE status = 'ACTIVE' OR status = 'PENDING'
//     *
//     * // Exclude multiple values with AND logic
//     * FilterDefinition(ROLE, NE, Arrays.asList("ADMIN", "ROOT"))
//     * → WHERE role != 'ADMIN' AND role != 'ROOT'
//     *
//     * // Age range
//     * FilterDefinition(AGE, RANGE, Arrays.asList(18, 65))
//     * → WHERE age BETWEEN 18 AND 65
//     *
//     * // IN operator with automatic conversion
//     * FilterDefinition(ID, IN, Arrays.asList("1", "2", "3"))
//     * → WHERE id IN (1, 2, 3)
//     * }</pre>
//     *
//     * <p><strong>Supported Types:</strong></p>
//     * <ul>
//     *   <li>Primitives: int, long, double, float, short, byte, boolean</li>
//     *   <li>Wrappers: Integer, Long, Double, Float, Short, Byte, Boolean</li>
//     *   <li>BigDecimal, BigInteger</li>
//     *   <li>String</li>
//     *   <li>Enums (with case-insensitive matching)</li>
//     *   <li>Dates: LocalDate, LocalDateTime, LocalTime, Instant, ZonedDateTime, java.util.Date</li>
//     *   <li>UUID</li>
//     *   <li>Any type with a String constructor</li>
//     * </ul>
//     *
//     * @param pathName The JPA property path (e.g., "name", "address.city.name")
//     * @param <E> The entity type
//     * @param <P> The property reference enum type
//     * @return A JPA resolver that can be used in queries
//     * @throws IllegalArgumentException if type conversion fails or path is invalid
//     */
//    @SuppressWarnings({"unchecked", "rawtypes"})
//    public static <E, P extends Enum<P> & PropertyReference> PredicateResolver<E> getResolverFromPath(
//            FilterConfig filterConfig,
//            String pathName,
//            Supplier<FilterDefinition<P>> definitionSupplier) {
//
//        return (Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
//            // Obtain filter parameter (completely accessible here)
//            FilterDefinition<P> definition = definitionSupplier.get();
//
//            // Apply Null value policy
//            definition = FilterConfigUtils.ensureNullValuePolicy(filterConfig, definition);
//
//            // IGNORE_FILTER: return neutral (always-true) condition
//            if (definition == null) {
//                return cb.conjunction();
//            }
//
//            // Resolve criteria path from path mapping
//            Path<?> path = PathResolverUtils.resolvePath(root, pathName);
//
//            // Get the expected Java type from the path
//            Class<?> pathJavaType = path.getJavaType();
//
//            // Convert value(s) to the appropriate type using configured enum match mode
//            Object convertedValue = convertValueForPath(definition, pathJavaType, filterConfig);
//
//            // Switch on supported operators to construct a predicate
//            return switch (definition.operator()) {
//                case EQ -> buildOrPredicateIfCollection(path, convertedValue, cb::equal, cb);
//                case NE -> buildAndPredicateIfCollection(path, convertedValue, cb::notEqual, cb);
//                case GT -> buildOrPredicateIfCollection(
//                        castToComparablePath(path),
//                        convertedValue,
//                        (Expression<Comparable> p, Object v) -> cb.greaterThan(p, (Comparable) castToComparable(v)),
//                        cb
//                );
//                case GTE -> buildOrPredicateIfCollection(
//                        castToComparablePath(path),
//                        convertedValue,
//                        (Expression<Comparable> p, Object v) -> cb.greaterThanOrEqualTo(p, (Comparable) castToComparable(v)),
//                        cb
//                );
//                case LT -> buildOrPredicateIfCollection(
//                        castToComparablePath(path),
//                        convertedValue,
//                        (Expression<Comparable> p, Object v) -> cb.lessThan(p, (Comparable) castToComparable(v)),
//                        cb
//                );
//                case LTE -> buildOrPredicateIfCollection(
//                        castToComparablePath(path),
//                        convertedValue,
//                        (Expression<Comparable> p, Object v) -> cb.lessThanOrEqualTo(p, (Comparable) castToComparable(v)),
//                        cb
//                );
//
//                case MATCHES -> {
//                    // Apply configured string-case strategy for LIKE
//                    Expression<String> expPath = castToStringPath(path);
//                    Object adjustedValue = TypeConversionUtils.applyStringCaseStrategy(convertedValue, filterConfig.getStringCaseStrategy());
//                    Expression<String> pathExpr = switch (filterConfig.getStringCaseStrategy()) {
//                        case LOWER -> cb.lower(expPath);
//                        case UPPER -> cb.upper(expPath);
//                        default -> expPath;
//                    };
//                    yield buildOrPredicateIfCollection(pathExpr, adjustedValue,
//                        (Expression<String> p, Object v) -> cb.like(p, castToString(v)), cb);
//                }
//                case NOT_MATCHES -> {
//                    // Apply configured string-case strategy for NOT LIKE
//                    Expression<String> expPath = castToStringPath(path);
//                    Object adjustedValue = TypeConversionUtils.applyStringCaseStrategy(convertedValue, filterConfig.getStringCaseStrategy());
//                    Expression<String> pathExpr = switch (filterConfig.getStringCaseStrategy()) {
//                        case LOWER -> cb.lower(expPath);
//                        case UPPER -> cb.upper(expPath);
//                        default -> expPath;
//                    };
//                    yield buildAndPredicateIfCollection(pathExpr, adjustedValue,
//                        (Expression<String> p, Object v) -> cb.notLike(p, castToString(v)), cb);
//                }
//                case IN -> path.in(castToCollection(convertedValue));
//                case NOT_IN -> cb.not(path.in(castToCollection(convertedValue)));
//                case IS_NULL -> cb.isNull(path);
//                case NOT_NULL -> cb.isNotNull(path);
//                case RANGE -> {
//                    Collection<?> collection = castToCollection(convertedValue);
//                    Object[] valuesToCompare = collection.toArray();
//                    if (valuesToCompare.length != 2) {
//                        throw new IllegalArgumentException("RANGE operator requires exactly 2 values");
//                    }
//                    yield buildBetweenPredicate(path, valuesToCompare[0], valuesToCompare[1], cb);
//                }
//                case NOT_RANGE -> {
//                    Collection<?> collection = castToCollection(convertedValue);
//                    Object[] valuesToCompare = collection.toArray();
//                    if (valuesToCompare.length != 2) {
//                        throw new IllegalArgumentException("NOT_RANGE operator requires exactly 2 values");
//                    }
//                    Predicate between = buildBetweenPredicate(path, valuesToCompare[0], valuesToCompare[1], cb);
//                    yield cb.not(between);
//                }
//                case CUSTOM -> {
//                    Optional<CustomOperatorProvider> provider = OperatorProviderRegistry.getProvider(definition.op());
//                    if (provider.isPresent()) {
//                        yield provider.get().toResolver(definition).resolve((Root) root, query, cb);
//                    }
//                    throw new IllegalStateException("No provider found for CUSTOM operator <" + definition.op() + ">");
//                }
//            };
//        };
//    }
//
//    /**
//     * Converts the value from FilterDefinition to the appropriate type based on the path type
//     * and operator requirements (single value vs collection).
//     */
//    private static <P extends Enum<P> & PropertyReference> Object convertValueForPath(
//        FilterDefinition<P> definition, Class<?> targetType, FilterConfig filterConfig) {
//
//        Object value = definition.value();
//
//        if (value == null) {
//            return null;
//        }
//
//        // For operators that explicitly require collections (IN, NOT_IN, RANGE, NOT_RANGE)
//        if (requiresCollection(definition.operator())) {
//            return TypeConversionUtils.convertToTypedCollection(value, targetType, filterConfig.getEnumMatchMode());
//        }
//
//        // For singleton operators: if value is already a collection, keep it as collection
//        // This allows OR/AND logic for operators like EQ, NE, GT, etc.
//        if (value instanceof Collection || value.getClass().isArray()) {
//            return TypeConversionUtils.convertToTypedCollection(value, targetType, filterConfig.getEnumMatchMode());
//        }
//
//        // For operators with single values
//        return TypeConversionUtils.convertValue(targetType, value, filterConfig.getEnumMatchMode());
//    }
//
//    /**
//     * Check if operator requires a collection
//     */
//    private static boolean requiresCollection(Op operator) {
//        return operator == Op.IN
//                || operator == Op.NOT_IN
//                || operator == Op.RANGE
//                || operator == Op.NOT_RANGE;
//    }
//
//    /**
//     * Builds an OR predicate if value is a collection, otherwise returns a single predicate.
//     * For example: EQ with [1, 2, 3] becomes (field = 1 OR field = 2 OR field = 3)
//     */
//    private static <T> Predicate buildOrPredicateIfCollection(
//            Expression<T> expression,
//            Object value,
//            BiFunction<Expression<T>, Object, Predicate> predicateBuilder,
//            CriteriaBuilder cb) {
//
//        if (value instanceof Collection<?> values) {
//            if (values.isEmpty()) {
//                return cb.disjunction(); // Always false for empty OR
//            }
//
//            List<Predicate> predicates = values.stream()
//                    .map(v -> predicateBuilder.apply(expression, v))
//                    .toList();
//
//            return cb.or(predicates.toArray(new Predicate[0]));
//        }
//
//        return predicateBuilder.apply(expression, value);
//    }
//
//    /**
//     * Builds an AND predicate if value is a collection, otherwise returns a single predicate.
//     * For example: NE with [1, 2, 3] becomes (field != 1 AND field != 2 AND field != 3)
//     */
//    private static <T> Predicate buildAndPredicateIfCollection(
//            Expression<T> expression,
//            Object value,
//            BiFunction<Expression<T>, Object, Predicate> predicateBuilder,
//            CriteriaBuilder cb) {
//
//        if (value instanceof Collection<?> values) {
//            if (values.isEmpty()) {
//                return cb.conjunction(); // Always true for empty AND
//            }
//
//            List<Predicate> predicates = values.stream()
//                    .map(v -> predicateBuilder.apply(expression, v))
//                    .toList();
//
//            return cb.and(predicates.toArray(new Predicate[0]));
//        }
//
//        return predicateBuilder.apply(expression, value);
//    }
//
//    // ========================================
//    // JPA-Specific Safe Casting Methods
//    // ========================================
//
//    /**
//     * Safely casts a Path to a Comparable Path for comparison operations.
//     */
//    @SuppressWarnings("unchecked")
//    private static <T extends Comparable<? super T>> Path<T> castToComparablePath(Path<?> path) {
//        return (Path<T>) path;
//    }
//
//    /**
//     * Safely casts a Path to a String Path for LIKE operations.
//     */
//    @SuppressWarnings("unchecked")
//    private static Path<String> castToStringPath(Path<?> path) {
//        return (Path<String>) path;
//    }
//
//    /**
//     * Safely casts an Object to Comparable for JPA predicates.
//     */
//    private static <T extends Comparable<? super T>> Comparable<?> castToComparable(Object value) {
//        if (value instanceof Comparable<?> cmp) {
//            return cmp;
//        }
//        throw new IllegalArgumentException("Value is not Comparable: " + value);
//    }
//
//    /**
//     * Safely converts an Object to String for JPA LIKE operations.
//     */
//    private static String castToString(Object value) {
//        return value.toString();
//    }
//
//    /**
//     * Safely casts an Object to Collection for JPA IN operations.
//     */
//    private static Collection<?> castToCollection(Object value) {
//        if (value instanceof Collection) {
//            return (Collection<?>) value;
//        }
//        throw new IllegalArgumentException("Value is not a Collection: " + value);
//    }
//
//    /**
//     * Helper to build a BETWEEN predicate with proper generic suppression.
//     */
//    @SuppressWarnings({"rawtypes","unchecked"})
//    private static Predicate buildBetweenPredicate(Path<?> path, Object start, Object end, CriteriaBuilder cb) {
//        Path<? extends Comparable> comparablePath = castToComparablePath(path);
//        Comparable startValue = castToComparable(start);
//        Comparable endValue = castToComparable(end);
//        return cb.between((Expression) comparablePath, startValue, endValue);
//    }
//}
//
//
//
//
