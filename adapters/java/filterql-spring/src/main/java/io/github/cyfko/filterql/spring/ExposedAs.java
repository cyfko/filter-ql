package io.github.cyfko.filterql.spring;

import java.lang.annotation.*;
import io.github.cyfko.filterql.core.validation.Op;

/**
 * Customizes how a projected field is exposed in the generated enum or filter DSL.
 *
 * <p>Used alongside {@link io.github.cyfko.projection.Projected}, this annotation allows overriding the default
 * symbolic name and specifying supported filter operators.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Projected(from = "department.name")
 * @ExposedAs(value = "DEPARTMENT_NAME", operators = {Op.EQ, Op.LIKE})
 * private String departmentName;
 * }</pre>
 *
 * @see io.github.cyfko.projection.Projected
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
