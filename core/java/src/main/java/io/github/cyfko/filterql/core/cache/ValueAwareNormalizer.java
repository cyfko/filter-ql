package io.github.cyfko.filterql.core.cache;

import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.parsing.BooleanSimplifier;
import io.github.cyfko.filterql.core.parsing.FastPostfixConverter;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.util.List;
import java.util.Map;

/**
 * Normalizer that generates cache keys based on filter structure (property references and operators).
 * <p>
 * <strong>NOTE:</strong> Despite its name "ValueAware", this implementation currently produces
 * structure-only cache keys identical to {@link StructuralNormalizer}. It ignores both filter
 * names and filter values. This class is provided for future extensibility where value-aware
 * caching strategies may be implemented.
 * </p>
 * <p>
 * This normalizer provides high cache hit rates by treating filters with the same
 * structural composition as identical, regardless of:
 * </p>
 * <ul>
 *   <li>Filter names (e.g., "userFilter" vs "nameSearch")</li>
 *   <li>Filter values (e.g., "Alice" vs "Bob")</li>
 * </ul>
 *
 * <h2>Normalization Format</h2>
 * <p>Reversed Polish Notation (RPN) form where identifiers are</p>
 * <pre>
 * [&lt;filter reference&gt;]{@code :}[&lt;operator&gt;]
 * </pre>
 * <p><em>Note: Values are NOT currently included in cache keys</em></p>
 *
 * <h2>Example 1</h2>
 * <p>Given the filter request:</p>
 * <pre>{@code
 * {
 *   "filters": {
 *     "filtre1": { "ref": "STATUS", "op": "EQ", "value": "ACTIVE" },
 *     "filtre2": { "ref": "TIER", "op": "EQ", "value": "PREMIUM" },
 *     "filtre3": { "ref": "ACTIVE", "op": "EQ", "value": true },
 *     "filtre4": { "ref": "STATUS", "op": "NE", "value": "BANNED" }
 *   },
 *   "combineWith": "(((filtre1)) & filtre2 | (!filtre3 & filtre4) & filtre1)"
 * }
 * }</pre>
 *
 * <p>Produces normalized key in Reverse Polish Notation (RPN) form:</p>
 * <pre>
 * Structure: STATUS:EQ TIER:EQ & ACTIVE:EQ ! STATUS:NE & STATUS:EQ & |
 * </pre>
 *
 * <h2>Example 2</h2>
 * <p>Given the filter request:</p>
 * <pre>{@code
 * {
 *   "filters": {
 *     "filtre1": { "ref": "STATUS", "op": "EQ", "value": "ACTIVE" },
 *     "filtre2": { "ref": "TIER", "op": "EQ", "value": "PREMIUM" },
 *     "filtre3": { "ref": "ACTIVE", "op": "EQ", "value": true },
 *     "filtre4": { "ref": "STATUS", "op": "NE", "value": "BANNED" }
 *   },
 *   "combineWith": "and" // <- shorthand syntax for global AND on all filters
 * }
 * }</pre>
 *
 * <p>Produces normalized key in Reverse Polish Notation (RPN) form:</p>
 * <pre>
 * Structure: STATUS:EQ TIER:EQ & ACTIVE:EQ STATUS:NE & &
 * </pre>
 *
 * <h2>Example 3</h2>
 * <p>Given the filter request:</p>
 * <pre>{@code
 * {
 *   "filters": {
 *     "filtre1": { "ref": "STATUS", "op": "EQ", "value": "ACTIVE" },
 *     "filtre2": { "ref": "TIER", "op": "EQ", "value": "PREMIUM" },
 *     "filtre3": { "ref": "ACTIVE", "op": "EQ", "value": true },
 *     "filtre4": { "ref": "STATUS", "op": "NE", "value": "BANNED" }
 *   },
 *   "combineWith": "or" // <- shorthand syntax for global OR on all filters
 * }
 * }</pre>
 *
 * <p>Produces normalized key in Reverse Polish Notation (RPN) form:</p>
 * <pre>
 * Structure: STATUS:EQ TIER:EQ | ACTIVE:EQ STATUS:NE | |
 * </pre>
 *
 * <h2>Example 4</h2>
 * <p>Given the filter request:</p>
 * <pre>{@code
 * {
 *   "filters": {
 *     "filtre1": { "ref": "STATUS", "op": "EQ", "value": "ACTIVE" },
 *     "filtre2": { "ref": "TIER", "op": "EQ", "value": "PREMIUM" },
 *     "filtre3": { "ref": "ACTIVE", "op": "EQ", "value": true },
 *     "filtre4": { "ref": "STATUS", "op": "NE", "value": "BANNED" }
 *   },
 *   "combineWith": "not" // <- shorthand syntax for global NOT on all filters
 * }
 * }</pre>
 *
 * <p>Produces normalized key in Reverse Polish Notation (RPN) form:</p>
 * <pre>
 * Structure: STATUS:EQ TIER:EQ & ACTIVE:EQ STATUS:NE & & !
 * </pre>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li>High cache hit rate (structural patterns repeat frequently)</li>
 *   <li>Minimal cache entries (finite combinations of properties and operators)</li>
 *   <li>Filter name independence (client naming conventions irrelevant)</li>
 *   <li>Value independence (values applied dynamically after cache hit)</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class ValueAwareNormalizer implements DslNormalizer {

    /**
     * Normalizes a filter request based on structure only (property references and operators).
     * <p>
     * <strong>NOTE:</strong> Despite the class name, filter values are NOT currently included
     * in the normalized cache key. This method produces structure-only keys.
     * </p>
     *
     * @param request the filter request to normalize
     * @param <P> the property reference enum type
     * @return normalized cache key in RPN format: "PROP1:OP1 PROP2:OP2 &amp; ..."
     * @throws IllegalArgumentException if request is null or contains invalid data
     */
    @Override
    public <P extends Enum<P> & PropertyReference> String normalize(FilterRequest<P> request) {
        if (request == null) {
            throw new IllegalArgumentException("FilterRequest cannot be null");
        }

        // 1. Transform to RPN notation
        DslPolicy policy = DslPolicy.defaults();
        List<String> rpn = BooleanSimplifier.validateAndSimplifyPostfix(
                FastPostfixConverter.toPostfix(request.combineWith(), policy),
                policy
        );

        // 3. Normalize DSL structure (replace identifiers with '<property>:<op>')
        //noinspection unchecked
        return normalizeStructure((Map) request.filters(), rpn);
    }

    /**
     * Replace identifiers with '&lt;property&gt;:&lt;op&gt;'
     *
     * @param filters map of filter definitions.
     * @param identifiers the list of identifiers to replace
     * @return normalized and simplified structure string (compact, no spaces)
     */
    private String normalizeStructure(Map<String, FilterDefinition<?>> filters, List<String> identifiers) {
        StringBuilder normalized = new StringBuilder();

        for (String key : identifiers) {
            FilterDefinition<?> definition = filters.get(key);
            if  (definition != null) {
                normalized.append(definition.ref().name()).append(":").append(definition.op().toUpperCase());
            } else {
                normalized.append(key);
            }
            normalized.append(" ");
        }

        return normalized.toString().stripTrailing();
    }
}
