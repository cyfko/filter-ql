package io.github.cyfko.filterql.spring;

import java.lang.annotation.*;
import io.github.cyfko.filterql.core.validation.Op;

/**
 * Customizes how a projected field is exposed in the generated enum or filter DSL.
 *
 * <p>Used alongside {@link io.github.cyfko.projection.Projected}, this annotation allows overriding the default
 * symbolic name and specifying supported filter operators.</p>
 *
 * <h2>Regular Field Example</h2>
 * <pre>{@code
 * @Projected(from = "department.name")
 * @ExposedAs(value = "DEPARTMENT_NAME", operators = {Op.EQ, Op.LIKE})
 * private String departmentName;
 * }</pre>
 *
 * <h2>Virtual Field Example</h2>
 * <pre>{@code
 * @ExposedAs(value = "FULL_NAME", operators = {Op.MATCHES})
 * public static PredicateResolver<Person> fullNameMatches(String op, Object[] args) {
 *     return (root, query, cb) -> {
 *         String pattern = "%" + args[0] + "%";
 *         return cb.or(
 *             cb.like(root.get("firstName"), pattern),
 *             cb.like(root.get("lastName"), pattern)
 *         );
 *     };
 * }
 * }</pre>
 *
 * <p><b>Virtual field method requirements:</b></p>
 * <ul>
 *   <li>Must be {@code public static} (or instance method if managed by Spring)</li>
 *   <li>Return type: {@code PredicateResolver<E>} where E is the entity type</li>
 *   <li>Parameters: {@code (String op, Object[] args)} — the operator and filter arguments</li>
 * </ul>
 *
 * @see io.github.cyfko.projection.Projected
 * @see io.github.cyfko.filterql.core.spi.PredicateResolver
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExposedAs {

    /**
     * The symbolic name exposed in the generated enum or filter DSL.
     */
    String value();

    /**
     * Supported filter operators for this field.
     */
    Op[] operators() default {};

    /** If false, virtual field only used internally. */
    boolean exposed() default true;
}
