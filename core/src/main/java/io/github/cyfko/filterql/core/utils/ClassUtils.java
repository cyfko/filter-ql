package io.github.cyfko.filterql.core.utils;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced utilities for Java reflection and dynamic type manipulation,
 * especially for introspection and analysis of collections and class hierarchies.
 * <p>
 * This class provides comprehensive reflection utilities that are particularly useful
 * for framework development, dynamic mapping, and type-safe operations on collections.
 * All methods are designed to be thread-safe and performant with internal caching.
 * </p>
 * 
 * <p><strong>Core Capabilities:</strong></p>
 * <ul>
 *   <li><strong>Field Discovery:</strong> Hierarchical field searching with caching</li>
 *   <li><strong>Generic Type Extraction:</strong> Collection and parameterized type analysis</li>
 *   <li><strong>Type Hierarchy Analysis:</strong> Common superclass determination</li>
 *   <li><strong>Type Compatibility:</strong> Cross-collection type validation</li>
 *   <li><strong>Runtime Type Capture:</strong> Generic type preservation at runtime</li>
 * </ul>
 *
 * <h2>Comprehensive Usage Examples</h2>
 * 
 * <p><em>Field Discovery:</em></p>
 * <pre>{@code
 * public class User {
 *     private Long id;
 *     private String name;
 * }
 * 
 * public class AdminUser extends User {
 *     private Set<String> permissions;
 * }
 * 
 * // Search for inherited fields
 * Optional<Field> idField = ClassUtils.getAnyField(AdminUser.class, "id");
 * // Returns field from User class even though searching AdminUser
 * 
 * Optional<Field> permissionsField = ClassUtils.getAnyField(AdminUser.class, "permissions");
 * // Returns field from AdminUser class
 * }</pre>
 * 
 * <p><em>Generic Type Analysis:</em></p>
 * <pre>{@code
 * public class OrderRepository {
 *     private List<Order> orders;
 *     private Map<String, Customer> customerMap;
 *     private Set<Product> products;
 * }
 * 
 * // Extract generic types from collections
 * Field ordersField = OrderRepository.class.getDeclaredField("orders");
 * Optional<Type> orderType = ClassUtils.getCollectionGenericType(ordersField, 0);
 * // Returns: Order.class
 * 
 * Field mapField = OrderRepository.class.getDeclaredField("customerMap");
 * Optional<Type> keyType = ClassUtils.getCollectionGenericType(mapField, 0);    // String.class
 * Optional<Type> valueType = ClassUtils.getCollectionGenericType(mapField, 1);  // Customer.class
 * }</pre>
 * 
 * <p><em>Type Hierarchy and Compatibility:</em></p>
 * <pre>{@code
 * // Mixed collection type analysis
 * List<Number> numbers = List.of(1, 2L, 3.14, 4.5f);
 * Class<?> commonType = ClassUtils.getCommonSuperclass(numbers); // Number.class
 * 
 * List<String> strings = List.of("hello", "world");
 * Class<?> stringType = ClassUtils.getCommonSuperclass(strings);  // String.class
 * 
 * // Type compatibility validation
 * List<Integer> integers = List.of(1, 2, 3);
 * boolean compatible = ClassUtils.allCompatible(Number.class, integers); // true
 * boolean incompatible = ClassUtils.allCompatible(String.class, integers); // false
 * }</pre>
 * 
 * <p><em>Runtime Generic Type Capture:</em></p>
 * <pre>{@code
 * // Capture complex generic types at runtime
 * TypeReference<List<String>> listType = new TypeReference<List<String>>() {};
 * Class<List<String>> listClass = listType.getTypeClass(); // List.class
 * Type genericType = listType.getType(); // java.util.List<java.lang.String>
 * 
 * // Use in dynamic operations
 * TypeReference<Map<String, User>> mapType = new TypeReference<Map<String, User>>() {};
 * boolean canStore = mapType.isAssignableFrom(HashMap.class); // true
 * }</pre>
 * 
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li><strong>Caching:</strong> Field lookups and superclass computations are cached</li>
 *   <li><strong>Thread Safety:</strong> All methods are thread-safe using concurrent data structures</li>
 *   <li><strong>Memory Management:</strong> Caches can be cleared via {@link #clearCaches()}</li>
 *   <li><strong>Lazy Evaluation:</strong> Expensive operations are performed only when needed</li>
 * </ul>
 * 
 * <h2>Error Handling</h2>
 * <ul>
 *   <li>Null-safe: All methods handle null inputs gracefully</li>
 *   <li>Validation: Input parameters are validated with meaningful error messages</li>
 *   <li>Fail-fast: Invalid configurations are detected early</li>
 *   <li>Graceful fallbacks: Complex type scenarios fallback to Object.class</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see java.lang.reflect.Field
 * @see java.lang.reflect.Type
 * @see java.lang.reflect.ParameterizedType
 */
public final class ClassUtils {

    // Cache to improve performance of repetitive searches
    private static final Map<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Class<?>> SUPERCLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private ClassUtils() {
        // Utility class - no instantiation allowed
    }

    /**
     * Recursively searches for a named field in the class and its superclasses.
     * <p>
     * This method performs a depth-first search through the class hierarchy,
     * starting from the specified class and moving up to superclasses until
     * the field is found or the search reaches Object.class.
     * </p>
     * 
     * <p><strong>Search Strategy:</strong></p>
     * <ol>
     *   <li>Search in the provided class first</li>
     *   <li>If not found, search in the immediate superclass</li>
     *   <li>Continue up the hierarchy until found or exhausted</li>
     *   <li>Does not search in interfaces (only class hierarchy)</li>
     * </ol>
     * 
     * <p><strong>Caching:</strong> Results are cached for performance. The cache key 
     * combines class name and field name, so repeated lookups are very fast.</p>
     * 
     * <p><strong>Usage Examples:</strong></p>
     * <pre>{@code
     * // Basic field lookup
     * Optional<Field> nameField = ClassUtils.getAnyField(User.class, "name");
     * if (nameField.isPresent()) {
     *     Field field = nameField.get();
     *     Class<?> fieldType = field.getType();
     *     String fieldName = field.getName();
     * }
     * 
     * // Inherited field lookup
     * class BaseEntity {
     *     private Long id;
     * }
     * class User extends BaseEntity {
     *     private String name;
     * }
     * 
     * Optional<Field> idField = ClassUtils.getAnyField(User.class, "id");
     * // Successfully finds 'id' from BaseEntity even though searching User
     * 
     * // Missing field handling
     * Optional<Field> missingField = ClassUtils.getAnyField(User.class, "nonexistent");
     * if (missingField.isEmpty()) {
     *     System.out.println("Field not found in hierarchy");
     * }
     * }</pre>
     * 
     * <p><strong>Performance Notes:</strong></p>
     * <ul>
     *   <li>First lookup: O(h) where h is hierarchy depth</li>
     *   <li>Subsequent lookups: O(1) due to caching</li>
     *   <li>Memory usage: Cached entries use class+field name as key</li>
     * </ul>
     *
     * @param clazz the starting class for the search, not null
     * @param name  the name of the field to search for, not null
     * @return an {@link Optional} containing the field, or empty if no field with that name is found
     * @throws NullPointerException if clazz or name is null
     * @see Field
     * @see #clearCaches()
     */
    public static Optional<Field> getAnyField(Class<?> clazz, String name) {
        Objects.requireNonNull(clazz, "Class must not be null");
        Objects.requireNonNull(name, "Field name must not be null");

        String cacheKey = clazz.getName() + "#" + name;
        return FIELD_CACHE.computeIfAbsent(cacheKey, key -> {
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(name);
                    return Optional.of(field);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Dynamically extracts the generic type of a collection from a declared field.
     * <p>
     * This method analyzes the generic type information preserved in a field declaration
     * and extracts the specific parameter type at the given index. It handles complex 
     * generic scenarios including wildcards, nested parameterized types, and type variables.
     * </p>
     * 
     * <p><strong>Supported Field Types:</strong></p>
     * <ul>
     *   <li><strong>Collections:</strong> {@code List<String>}, {@code Set<User>}</li>
     *   <li><strong>Maps:</strong> {@code Map<String, User>} (use index 0 for key, 1 for value)</li>
     *   <li><strong>Custom Generics:</strong> {@code Repository<Entity, ID>}</li>
     *   <li><strong>Wildcards:</strong> {@code List<? extends Number>}</li>
     *   <li><strong>Nested Types:</strong> {@code Map<String, List<User>>}</li>
     * </ul>
     * 
     * <p><strong>Parameter Index Guide:</strong></p>
     * <table border="1">
     * <caption>Generic Type Parameter Indices</caption>
     * <tr><th>Field Type</th><th>Index 0</th><th>Index 1</th><th>Index 2+</th></tr>
     * <tr><td>List&lt;T&gt;</td><td>T</td><td>-</td><td>-</td></tr>
     * <tr><td>Set&lt;T&gt;</td><td>T</td><td>-</td><td>-</td></tr>
     * <tr><td>Map&lt;K,V&gt;</td><td>K</td><td>V</td><td>-</td></tr>
     * <tr><td>Custom&lt;A,B,C&gt;</td><td>A</td><td>B</td><td>C</td></tr>
     * </table>
     * 
     * <p><strong>Complex Type Handling:</strong></p>
     * <pre>{@code
     * // Wildcard types
     * private List<? extends Number> numbers;
     * Optional<Type> type = getCollectionGenericType(field, 0); // Returns Number.class
     * 
     * // Bounded wildcards
     * private Set<? super Integer> integers;
     * Optional<Type> type = getCollectionGenericType(field, 0); // Returns Integer.class
     * 
     * // Nested generics
     * private Map<String, List<User>> userGroups;
     * Optional<Type> keyType = getCollectionGenericType(field, 0);   // String.class
     * Optional<Type> valueType = getCollectionGenericType(field, 1); // List<User> as ParameterizedType
     * 
     * // Generic arrays
     * private List<String[]> stringArrays;
     * Optional<Type> type = getCollectionGenericType(field, 0); // String[].class
     * }</pre>
     * 
     * <p><strong>Practical Usage Examples:</strong></p>
     * <pre>{@code
     * public class OrderService {
     *     private List<Order> orders;
     *     private Map<String, Customer> customers;
     *     private Set<Product> products;
     * }
     * 
     * Field ordersField = OrderService.class.getDeclaredField("orders");
     * Optional<Type> orderType = ClassUtils.getCollectionGenericType(ordersField, 0);
     * // Result: Optional.of(Order.class)
     * 
     * Field customersField = OrderService.class.getDeclaredField("customers");
     * Optional<Type> keyType = ClassUtils.getCollectionGenericType(customersField, 0);
     * Optional<Type> valueType = ClassUtils.getCollectionGenericType(customersField, 1);
     * // Results: Optional.of(String.class), Optional.of(Customer.class)
     * 
     * // Error case - index out of bounds
     * try {
     *     ClassUtils.getCollectionGenericType(ordersField, 1); // List<T> has only index 0
     * } catch (IndexOutOfBoundsException e) {
     *     // Handle gracefully
     * }
     * }</pre>
     * 
     * <p><strong>Edge Cases:</strong></p>
     * <ul>
     *   <li><strong>Raw Types:</strong> {@code List} (no generics) returns empty</li>
     *   <li><strong>Object Wildcards:</strong> {@code List<?>} returns Object.class</li>
     *   <li><strong>Type Variables:</strong> {@code List<T>} returns first bound or Object.class</li>
     *   <li><strong>Complex Bounds:</strong> Multiple bounds use first bound</li>
     * </ul>
     *
     * @param field      field representing a generic collection, not null
     * @param paramIndex index of the generic parameter ({@code 0} for List&lt;T&gt;, {@code 0} or {@code 1} for Map&lt;K,V&gt;)
     * @return an {@link Optional} containing the generic parameter type, or empty if undetermined
     * @throws NullPointerException if field is null
     * @throws IndexOutOfBoundsException if paramIndex is invalid for the field's generic type
     * @see ParameterizedType
     * @see WildcardType
     * @see TypeVariable
     * @see GenericArrayType
     */
    public static Optional<Type> getCollectionGenericType(Field field, int paramIndex) {
        Objects.requireNonNull(field, "Field must not be null");

        Type genericType = field.getGenericType();
        return extractGenericTypeAt(genericType, paramIndex);
    }

    /**
     * Extracts the generic type at a given index from a generic type.
     *
     * @param genericType the generic type to analyze
     * @param paramIndex the index of the parameter to extract
     * @return the extracted type or empty if not possible
     */
    private static Optional<Type> extractGenericTypeAt(Type genericType, int paramIndex) {
        if (!(genericType instanceof ParameterizedType)) {
            return Optional.empty();
        }

        ParameterizedType paramType = (ParameterizedType) genericType;
        Type[] typeArgs = paramType.getActualTypeArguments();

        if (paramIndex < 0 || paramIndex >= typeArgs.length) {
            throw new IndexOutOfBoundsException("Parameter index " + paramIndex + " out of bounds for " + Arrays.toString(typeArgs));
        }

        Type targetType = typeArgs[paramIndex];

        // Handle different types of generic parameters
        if (targetType instanceof WildcardType) {
            return handleWildcardType((WildcardType) targetType);
        } else if (targetType instanceof GenericArrayType) {
            return handleGenericArrayType((GenericArrayType) targetType);
        } else if (targetType instanceof TypeVariable) {
            return handleTypeVariable((TypeVariable<?>) targetType);
        }

        return Optional.of(targetType);
    }

    /**
     * Handles wildcard types (? extends Type, ? super Type).
     */
    private static Optional<Type> handleWildcardType(WildcardType wildcardType) {
        Type[] upperBounds = wildcardType.getUpperBounds();
        Type[] lowerBounds = wildcardType.getLowerBounds();

        // ? extends Type -> take the upper bound
        if (upperBounds.length > 0 && upperBounds[0] != Object.class) {
            return Optional.of(upperBounds[0]);
        }

        // ? super Type -> take the lower bound
        if (lowerBounds.length > 0) {
            return Optional.of(lowerBounds[0]);
        }

        // ? alone -> Object
        return Optional.of(Object.class);
    }

    /**
     * Handles generic arrays (T[], List<String>[]).
     */
    private static Optional<Type> handleGenericArrayType(GenericArrayType arrayType) {
        Type componentType = arrayType.getGenericComponentType();
        return Optional.of(componentType);
    }

    /**
     * Handles type variables (T, K, V, etc.).
     */
    private static Optional<Type> handleTypeVariable(TypeVariable<?> typeVar) {
        Type[] bounds = typeVar.getBounds();
        // Return the first bound (usually Object if no constraint)
        return bounds.length > 0 ? Optional.of(bounds[0]) : Optional.of(Object.class);
    }

    /**
     * Determines the highest (most common) class in the hierarchy of elements in a collection.
     * Ignores null elements. Returns {@code Object.class} if all are null.
     * Optimized version with cache for better performance.
     *
     * @param collection reference collection, not null and not empty
     * @param <T>        type of the collection elements
     * @return the common ancestor class or {@code Object.class} if all elements are null
     * @throws NullPointerException     if the collection is null
     * @throws IllegalArgumentException if the collection is empty
     */
    public static <T> Class<? super T> getCommonSuperclass(Collection<T> collection) {
        Objects.requireNonNull(collection, "Collection must not be null");
        if (collection.isEmpty()) {
            throw new IllegalArgumentException("Collection must not be empty");
        }

        // Collect all unique types
        Set<Class<?>> uniqueTypes = new LinkedHashSet<>();
        for (T element : collection) {
            if (element != null) {
                uniqueTypes.add(element.getClass());
            }
        }

        if (uniqueTypes.isEmpty()) {
            return Object.class;
        }

        if (uniqueTypes.size() == 1) {
            return (Class<? super T>) uniqueTypes.iterator().next();
        }

        // Compute common class with cache
        String cacheKey = uniqueTypes.stream()
                .map(Class::getName)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        return (Class<? super T>) SUPERCLASS_CACHE.computeIfAbsent(cacheKey, key ->
                computeCommonSuperclass(uniqueTypes)
        );
    }

    /**
     * Computes the common class among several types.
     */
    private static Class<?> computeCommonSuperclass(Set<Class<?>> types) {
        Class<?> result = null;

        for (Class<?> type : types) {
            if (result == null) {
                result = type;
            } else {
                result = findCommonSuperclass(result, type);
            }
        }

        return result != null ? result : Object.class;
    }

    /**
     * Finds the common class between two specific types.
     */
    private static Class<?> findCommonSuperclass(Class<?> class1, Class<?> class2) {
        if (class1.isAssignableFrom(class2)) {
            return class1;
        }
        if (class2.isAssignableFrom(class1)) {
            return class2;
        }

        // Traverse the hierarchy of class1 until finding a common ancestor
        Class<?> current = class1;
        while (current != null && current != Object.class) {
            if (current.isAssignableFrom(class2)) {
                return current;
            }
            current = current.getSuperclass();
        }

        return Object.class;
    }

    /**
     * Checks if all non-null elements in a collection are strictly of the same concrete type.
     *
     * @param collection collection to test, not null
     * @return an {@link Optional} containing the common class, or empty if types differ or all are null
     * @throws NullPointerException if the collection is null
     */
    public static Optional<Class<?>> getCommonClass(Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection must not be null");
        if (collection.isEmpty()) {
            return Optional.empty();
        }

        Class<?> commonClass = null;
        for (Object element : collection) {
            if (element == null) {
                continue;
            }

            if (commonClass == null) {
                commonClass = element.getClass();
            } else if (!commonClass.equals(element.getClass())) {
                return Optional.empty();
            }
        }

        return Optional.ofNullable(commonClass);
    }

    /**
     * Checks that all non-null elements in a collection are compatible with each other
     * with respect to a given base class.
     * <p>
     * Compatibility means there is a subclass or superclass relationship
     * between {@code baseClass} and the class of each non-null element in the collection.
     * In other words, each element must be an instance of {@code baseClass} or a parent class,
     * or conversely {@code baseClass} must be assignable to a variable of the element's type.
     * </p>
     *
     * <p>Null elements are ignored in the check.
     * If the collection is empty, the method throws an {@link IllegalStateException}.</p>
     *
     * @param baseClass  the reference class for compatibility, not null
     * @param collection the collection of elements to check, not null and not empty
     * @param <T>        the type of elements in the collection
     * @return {@code true} if all (non-null) elements are compatible with {@code baseClass}, {@code false} otherwise
     * @throws NullPointerException     if {@code baseClass} or {@code collection} is {@code null}
     * @throws IllegalStateException    if the collection is empty
     *
     * @implNote
     * This method uses {@link Class#isAssignableFrom(Class)} to test type compatibility,
     * considering inheritance and implementation relationships.
     */
    public static <T> boolean allCompatible(Class<?> baseClass, Collection<T> collection) {
        Objects.requireNonNull(baseClass, "Base class must not be null");
        Objects.requireNonNull(collection, "Collection must not be null");
        if (collection.isEmpty()) {
            throw new IllegalStateException("Collection must not be empty");
        }

        for (T element : collection) {
            if (element == null) {
                continue;
            }

            Class<?> elementClass = element.getClass();
            if (!baseClass.isAssignableFrom(elementClass) && !elementClass.isAssignableFrom(baseClass)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clears internal caches. Useful for tests or memory management in long-running applications.
     */
    public static void clearCaches() {
        FIELD_CACHE.clear();
        SUPERCLASS_CACHE.clear();
    }

    /**
     * Returns cache statistics for monitoring.
     *
     * @return a map containing the cache sizes
     */
    public static Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("fieldCacheSize", FIELD_CACHE.size());
        stats.put("superclassCacheSize", SUPERCLASS_CACHE.size());
        return Collections.unmodifiableMap(stats);
    }

    /**
     * Utility method to obtain the {@link Class} object for the generic type {@code T}.
     *
     * @param <T> the generic type
     * @return the {@link Class} of {@code T}
     */
    public static <T> Class<T> getClazz() {
        return (new TypeReference<T>() {}).getTypeClass();
    }

    /**
     * Utility class for dynamically capturing the actual type of a generic parameter at instantiation.
     * Secure version against multiple inheritance attacks and with enhanced validation.
     * <p>
     * Now handles complex types and offers better security.
     * </p>
     *
     * @param <T> generic type to capture
     */
    public static abstract class TypeReference<T> {
        private final Class<T> typeClass;
        private final Type type;

        /**
         * Protected constructor for TypeReference.
         * Must be called from an anonymous subclass to capture the generic type parameter.
         */
        @SuppressWarnings("unchecked")
        protected TypeReference() {
            // Security validation against multiple inheritance
            Class<?> thisClass = getClass();
            if (!thisClass.getName().contains("$") && thisClass != TypeReference.class) {
                throw new IllegalStateException("TypeReference ne doit pas être étendu par une classe nommée. Utilisez une classe anonyme.");
            }

            Type superClass = thisClass.getGenericSuperclass();
            if (!(superClass instanceof ParameterizedType)) {
                throw new IllegalArgumentException("TypeReference doit être instancié avec un type générique explicite");
            }

            ParameterizedType paramType = (ParameterizedType) superClass;
            Type[] typeArgs = paramType.getActualTypeArguments();

            if (typeArgs.length != 1) {
                throw new IllegalArgumentException("TypeReference doit avoir exactement un paramètre de type");
            }

            this.type = typeArgs[0];

            // Resolution of the actual type
            if (type instanceof Class<?>) {
                this.typeClass = (Class<T>) type;
            } else if (type instanceof ParameterizedType) {
                Type rawType = ((ParameterizedType) type).getRawType();
                if (rawType instanceof Class<?>) {
                    this.typeClass = (Class<T>) rawType;
                } else {
                    throw new IllegalArgumentException("Type générique trop complexe: " + type);
                }
            } else if (type instanceof WildcardType) {
                WildcardType wildcard = (WildcardType) type;
                Type[] upperBounds = wildcard.getUpperBounds();
                if (upperBounds.length > 0 && upperBounds[0] instanceof Class<?>) {
                    this.typeClass = (Class<T>) upperBounds[0];
                } else {
                    this.typeClass = (Class<T>) Object.class;
                }
            } else if (type instanceof TypeVariable<?>) {
                TypeVariable<?> typeVar = (TypeVariable<?>) type;
                Type[] bounds = typeVar.getBounds();
                if (bounds.length > 0 && bounds[0] instanceof Class<?>) {
                    this.typeClass = (Class<T>) bounds[0];
                } else {
                    throw new IllegalArgumentException("Impossible d'inférer la classe à partir des bornes de type : " + Arrays.toString(bounds));
                }
            } else {
                throw new IllegalArgumentException("Type générique non supporté: " + type.getClass().getName());
            }
        }

    /**
     * Returns the captured class corresponding to the generic type T.
     *
     * @return the class representing T
     */
    public final Class<T> getTypeClass() {
            return this.typeClass;
        }

    /**
     * Returns the captured {@link Type}, such as a parameterized type or a concrete class.
     *
     * @return an instance of {@link Type} corresponding to T
     */
    public final Type getType() {
            return this.type;
        }

    /**
     * Checks if the captured type is assignable from a given class.
     *
     * @param clazz the class to test
     * @return true if compatible
     */
    public final boolean isAssignableFrom(Class<?> clazz) {
            return this.typeClass.isAssignableFrom(clazz);
        }

    /**
     * Checks if the captured type is an instance of a given class.
     *
     * @param clazz the class to test
     * @return true if instance
     */
    public final boolean isInstanceOf(Class<?> clazz) {
            return clazz.isAssignableFrom(this.typeClass);
        }

        @Override
        public final boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TypeReference)) return false;
            TypeReference<?> other = (TypeReference<?>) obj;
            return Objects.equals(this.type, other.type);
        }

        @Override
        public final int hashCode() {
            return Objects.hash(type);
        }

        @Override
        public final String toString() {
            return "TypeReference<" + type.getTypeName() + ">";
        }
    }
}