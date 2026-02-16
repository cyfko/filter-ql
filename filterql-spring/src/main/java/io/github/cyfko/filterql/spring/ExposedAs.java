package io.github.cyfko.filterql.spring;

import java.lang.annotation.*;
import io.github.cyfko.filterql.core.api.Op;

/**
 * Customizes how a field is exposed in the filter request criteria.
 *
 * <p>This annotation serves two distinct purposes:
 * <ol>
 *   <li><b>Projection customization</b> — Override the symbolic name and restrict filter operators
 *       for a projected field</li>
 *   <li><b>Virtual field definition</b> — Define filter properties that don't map directly
 *       to entity fields</li>
 * </ol>
 *
 * <h2>Use Case 1: Projection Customization</h2>
 *
 * <p>When used on a getter method alongside {@link io.github.cyfko.projection.Projected}, this annotation
 * overrides the default symbolic name and restricts which operators are allowed.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public interface EmployeeDTO {
 *
 *     // Default behavior (without @ExposedAs)
 *     @Projected(from = "department.name")
 *     String getDepartmentName();  // Symbolic name: "DEPARTMENT_NAME"
 *
 *     // Custom symbolic name and restricted operators
 *     @Projected(from = "department.name")
 *     @ExposedAs(value = "DEPARTMENT_NAME_1", operators = {Op.EQ, Op.LIKE})
 *     String getDepartmentName();  // Symbolic name: "DEPARTMENT_NAME_1"
 * }
 * }</pre>
 *
 * <h2>Use Case 2: Virtual Field Definition</h2>
 *
 * <p>When used on a method returning {@link io.github.cyfko.filterql.jpa.spi.PredicateResolver},
 * this annotation defines a <b>virtual filter property</b> — a filter criterion that doesn't
 * correspond to a single entity field.
 *
 * <p><b>Important:</b> Virtual field resolver methods are <b>only searched within the provider classes</b>
 * declared in {@link io.github.cyfko.projection.Projection#providers()}. They are not searched in the
 * projection itself.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // DTO declaration with providers
 * @Projection(from = Person.class, providers = {
 *     @Provider(PersonFilters.class)
 * })
 * public interface PersonDTO {
 *
 *     @Projected
 *     String getFirstName();
 *
 *     @Projected
 *     String getLastName();
 * }
 *
 * // Virtual field resolver in provider class
 * public class PersonFilters {
 *
 *     @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
 *     public static PredicateResolver<Person> fullNameMatches(String op, Object[] args) {
 *         return (root, query, cb) -> {
 *             String pattern = "%" + args[0] + "%";
 *             return cb.or(
 *                 cb.like(root.get("firstName"), pattern),
 *                 cb.like(root.get("lastName"), pattern)
 *             );
 *         };
 *     }
 * }
 * }</pre>
 *
 * <h2>Virtual Field Method Requirements</h2>
 *
 * <p>Methods defining virtual fields must:
 * <ul>
 *   <li>Be declared in a class registered via {@link io.github.cyfko.projection.Projection#providers()}</li>
 *   <li>Follow this signature: {@code public static PredicateResolver<EntityType> methodName(String op, Object[] args)}</li>
 *   <li>Be annotated with {@code @ExposedAs}</li>
 * </ul>
 *
 * <p><b>Method signature breakdown:</b>
 * <ul>
 *   <li><b>Visibility:</b> Must be {@code public static} (or instance method if the provider is a managed bean)</li>
 *   <li><b>Return type:</b> {@code PredicateResolver<E>} where {@code E} is the entity class</li>
 *   <li><b>Parameters:</b>
 *     <ul>
 *       <li>{@code String op} — The operator (e.g., "EQ", "MATCHES")</li>
 *       <li>{@code Object[] args} — The filter arguments</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Provider Resolution</h2>
 *
 * <p>Virtual field resolvers are discovered using the same mechanism as {@link io.github.cyfko.projection.Computed}
 * field providers:
 * <ol>
 *   <li>The system searches all classes declared in {@link io.github.cyfko.projection.Projection#providers()}</li>
 *   <li>If {@link io.github.cyfko.projection.Provider#bean()} is specified, the provider is resolved from the
 *       IoC container; otherwise, a static method is required</li>
 *   <li>Providers are evaluated in declaration order (first-match-wins)</li>
 * </ol>
 *
 * <h3>Example with Bean Provider</h3>
 * <pre>{@code
 * // DTO with bean-based provider
 * @Projection(from = User.class, providers = {
 *     @Provider(value = UserFilters.class, bean = "userFilterService")
 * })
 * public interface UserDTO {
 *     // ...
 * }
 *
 * // Spring-managed provider
 * @Service("userFilterService")
 * public class UserFilters {
 *
 *     @Autowired
 *     private SomeService someService;
 *
 *     @ExposedAs(value = "CUSTOM_FILTER", operators = {Op.EQ})
 *     public PredicateResolver<User> customFilter(String op, Object[] args) {
 *         return (root, query, cb) -> {
 *             // Can use injected dependencies
 *             return cb.equal(root.get("status"), someService.resolveStatus(args[0]));
 *         };
 *     }
 * }
 * }</pre>
 *
 * @see io.github.cyfko.projection.Projected
 * @see io.github.cyfko.projection.Projection
 * @see io.github.cyfko.projection.Provider
 * @see io.github.cyfko.filterql.jpa.spi.PredicateResolver
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposedAs {

    /**
     * The symbolic name exposed in the generated enum or filter criteria.
     */
    String value();

    /**
     * Supported filter operators for this field.
     */
    Op[] operators() default {};

    /** If false, virtual field only used internally. */
    boolean exposed() default true;
}
