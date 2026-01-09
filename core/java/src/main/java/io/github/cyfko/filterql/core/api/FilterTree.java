package io.github.cyfko.filterql.core.api;

import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.util.Map;

/**
 * Represents a parsed expression tree derived from a domain-specific language (DSL) filter expression.
 * <p>
 * A {@code FilterTree} encapsulates the logical structure of composed filters and boolean operators,
 * allowing the generation of a global boolean condition by resolving filter references with a provided context.
 * This abstraction supports complex boolean expressions combining multiple filters with AND, OR, and NOT operations.
 * </p>
 *
 * <p><strong>Example DSL Expressions:</strong></p>
 * <ul>
 *   <li>{@code "filter1"} - Single filter reference</li>
 *   <li>{@code "filter1 & filter2"} - Logical AND of two filters</li>
 *   <li>{@code "filter1 | filter2"} - Logical OR of two filters</li>
 *   <li>{@code "!filter1"} - Logical negation of a filter</li>
 *   <li>{@code "(filter1 & filter2) | !filter3"} - Nested, complex boolean expression</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Parsing the DSL expression into a filter tree
 * DslParser parser = new BasicDslParser();
 * FilterTree tree = parser.parse("(nameFilter & ageFilter) | statusFilter");
 *
 * // Prepare filter definitions
 * Map<String, FilterDefinition<UserPropertyRef>> definitions = // filter definitions
 * FilterContext context = // implementation-defined context;
 *
 * // Generate the combined condition representing the entire expression
 * Condition result = tree.generate(definitions, context);
 * // Example interpreted as: (name LIKE 'John%' AND age > 25) OR status = 'ACTIVE'
 * }</pre>
 *
 * <p><strong>Implementation Notes:</strong></p>
 * <ul>
 *   <li>Instances are immutable after parsing to ensure thread-safety and reuse.</li>
 *   <li>The same {@code FilterTree} instance can be used to generate different conditions with different contexts.</li>
 *   <li>Validation and resolution of filter keys occur during generation, not at parse-time.</li>
 *   <li>Operator precedence follows: NOT (&quot;!&quot;) &gt; AND (&quot;&amp;&quot;) &gt; OR (&quot;|&quot;).</li>
 * </ul>
 *
 * @see DslParser
 * @see FilterContext
 * @see Condition
 * @author Frank KOSSI
 * @since 4.0.0
 */
@FunctionalInterface
public interface FilterTree {

    /**
     * Generates a complete {@link Condition} representing the entire filter tree by resolving all filter references
     * with the provided {@link FilterContext}.
     * <p>
     * The process involves:
     * </p>
     * <ol>
     *  <li>Traversing the expression tree in post-order (evaluating leaves before parents)</li>
     *  <li>Resolving each filter key reference to a {@link Condition} via {@link FilterContext#toCondition(String, Enum, String)}</li>
     *  <li>Applying boolean operations AND, OR, and NOT to combine these conditions according to the expression structure</li>
     *  <li>Returning the root {@link Condition} representing the full logical expression</li>
     * </ol>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * // Given DSL: "(active & premium) | !deleted"
     * // Filter definitions map filter keys to their property references and operators
     * Map<String, FilterDefinition<UserPropertyRef>> definitions = Map.of(
     *     "active", new FilterDefinition<>(UserPropertyRef.STATUS, "EQ", "ACTIVE"),
     *     "premium", new FilterDefinition<>(UserPropertyRef.TYPE, "EQ", "PREMIUM"),
     *     "deleted", new FilterDefinition<>(UserPropertyRef.DELETED, "EQ", true)
     * );
     *
     * Condition result = tree.generate(definitions, context);
     * // Result logically: (status = 'ACTIVE' AND type = 'PREMIUM') OR NOT(deleted = true)
     * }</pre>
     *
     * @param <P> the enum type extending {@link PropertyReference}
     * @param definitions A map of filter keys to their {@link FilterDefinition}s used to generate conditions
     * @param filterContext the context providing condition resolution for all filter keys in the expression
     * @return the fully resolved {@link Condition} representing the filter expression
     * @throws FilterValidationException if a filter validation fails during condition resolution
     * @throws DSLSyntaxException if the expression refers to undefined or invalid filter keys
     * @throws NullPointerException if definitions or filterContext is null
     */
    <P extends Enum<P> & PropertyReference> Condition generate(
        Map<String, FilterDefinition<P>> definitions,
        FilterContext filterContext
    ) throws FilterValidationException, DSLSyntaxException;
}
