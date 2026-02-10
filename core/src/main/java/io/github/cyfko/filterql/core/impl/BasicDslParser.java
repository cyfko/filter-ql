package io.github.cyfko.filterql.core.impl;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.api.DslParser;
import io.github.cyfko.filterql.core.cache.BoundedLRUCache;
import io.github.cyfko.filterql.core.cache.StructuralNormalizer;
import io.github.cyfko.filterql.core.config.CachePolicy;
import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.parsing.BooleanSimplifier;
import io.github.cyfko.filterql.core.parsing.FastPostfixConverter;
import io.github.cyfko.filterql.core.parsing.PostfixConditionBuilder;
import io.github.cyfko.filterql.core.api.PropertyReference;

import java.util.*;

/**
 * Optimized implementation of a parser for a specialized language (DSL) that converts
 * textual expressions into filter tree structures {@link FilterTree}.
 * <p>
 * The parser supports the following logical operators:
 * <ul>
 *   <li>&amp; (AND) - precedence: 2, associativity: left</li>
 *   <li>| (OR) - precedence: 1, associativity: left</li>
 *   <li>! (NOT) - precedence: 3, associativity: right</li>
 * </ul>
 * as well as parentheses for managing priorities.
 *
 * <p>Token should conform to the underlying {@link DslPolicy}.
 *
 * <h2>Performance Benefits</h2>
 * <ul>
 *   <li><strong>O(n) Complexity</strong>: Single pass in each phase, no backtracking</li>
 *   <li><strong>Minimal Allocations</strong>: No Token objects, no intermediate AST nodes</li>
 *   <li><strong>Boolean Simplification</strong>: Reduces cache key size and redundant operations</li>
 *   <li><strong>Lazy Evaluation</strong>: Defers Condition construction until needed</li>
 *   <li><strong>Intelligent Caching</strong>: Structure-based LRU cache for generated conditions (configurable)</li>
 *   <li><strong>Fail-Fast Validation</strong>: Rejects malformed expressions early</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * <p>
 * The parser includes an optional LRU cache for generated conditions (v4.0.1+):
 * </p>
 * <ul>
 *   <li><strong>Cache Level</strong>: {@link FilterTree#generate(Map, FilterContext)} (not parse())</li>
 *   <li><strong>Cache Key</strong>: Structural signature (filter definitions + simplified postfix)</li>
 *   <li><strong>Cache Value</strong>: Generated {@link Condition} instances</li>
 *   <li><strong>Cache Hit</strong>: Skips Condition construction, returns cached result instantly</li>
 *   <li><strong>Thread-Safe</strong>: Uses ReadWriteLock for concurrent access</li>
 *   <li><strong>Memory Bounded</strong>: LRU eviction when cache is full</li>
 *   <li><strong>Configurable</strong>: Enable/disable via {@link CachePolicy}</li>
 *   <li><strong>Structure-Based</strong>: Different expressions with same structure share cache entry</li>
 * </ul>
 *
 * <h2>DoS Protection (Complexity Limits)</h2>
 * <p>
 * The parser enforces configurable complexity limits via {@link DslPolicy} to prevent
 * Denial of Service attacks through excessively complex expressions:
 * </p>
 * <ul>
 *   <li><strong>Expression Length</strong>: Rejects expressions exceeding configured character size</li>
 * </ul>
 *
 * <h2>Shorthand Syntax</h2>
 * <p>
 * For convenience, the parser supports shorthand syntax for common patterns:
 * </p>
 * <ul>
 *   <li><strong>"AND"</strong>: Combines all filters with AND operation</li>
 *   <li><strong>"OR"</strong>: Combines all filters with OR operation</li>
 *   <li><strong>"NOT"</strong>: Combines all filters with AND, then negates the result</li>
 * </ul>
 *
 * <h2>Usage examples</h2>
 * <pre>{@code
 * // Default configuration (balanced limits)
 * DSLParser parser = new DSLParser();
 * FilterTree tree = parser.parse("!(A & B) | C");
 *
 * // Strict configuration (for public APIs)
 * DSLParser strictParser = new DSLParser(DslPolicy.strict(), CachePolicy.strict());
 * FilterTree tree = strictParser.parse("A & B");
 *
 * // Shorthand syntax
 * FilterTree tree = parser.parse("AND"); // Combines all filters with AND
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class BasicDslParser implements DslParser {

    private final DslPolicy dslPolicy;
    private final CachePolicy cachePolicy;
    protected final BoundedLRUCache<String, Condition> cache;

    /**
     * Default constructor using {@link DslPolicy#defaults()}.
     * <p>
     * Applies balanced complexity limits suitable for most production use cases.
     * </p>
     */
    public BasicDslParser() {
        this(DslPolicy.defaults(), CachePolicy.defaults());
    }

    /**
     * Constructor with custom configuration.
     * <p>
     * Allows fine-grained control over complexity limits. Default cache policy is applied.
     * </p>
     *
     * @param dslPolicy the parser configuration with complexity limits
     * @throws IllegalArgumentException if config is null
     */
    public BasicDslParser(DslPolicy dslPolicy) {
        this(dslPolicy, CachePolicy.defaults());
    }

    /**
     * Constructor with custom configuration.
     * <p>
     * Allows fine-grained control over complexity limits and caching strategy.
     * </p>
     *
     * @param dslPolicy the parser configuration with complexity limits
     * @param cachePolicy the cache policy settings
     * @throws IllegalArgumentException if config is null
     */
    public BasicDslParser(DslPolicy dslPolicy, CachePolicy cachePolicy) {
        if (dslPolicy == null) {
            throw new IllegalArgumentException("DSL policy is required");
        }

        if (cachePolicy == null) {
            throw new IllegalArgumentException("Cache policy is required");
        }

        this.dslPolicy = dslPolicy;
        this.cachePolicy = cachePolicy;
        this.cache = cachePolicy.cacheEnabled()
            ? new BoundedLRUCache<>(cachePolicy.cacheSize())
            : null;
    }

    /**
     * Clears the parser cache (if enabled).
     * <p>
     * Useful for forcing re-parsing or freeing memory in long-running applications.
     * </p>
     */
    public void clearCache() {
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Returns cache statistics (if caching is enabled).
     *
     * @return map containing cache statistics (size, hit rate, etc.) or empty map if cache disabled
     */
    public Map<String, Object> getCacheStats() {
        if (cache == null) {
            return Map.of("enabled", false);
        }

        return Map.of(
            "enabled", true,
            "size", cache.size(),
            "maxSize", cachePolicy.cacheSize()
        );
    }

    /**
     * Parses the given expression string into a {@link FilterTree} using optimized three-phase system with caching.
     * <p>
     * <strong>Three-Phase Architecture (v4.0.0+)</strong>:
     * </p>
     * <ol>
     *   <li><strong>Phase 1</strong>: {@link FastPostfixConverter#toPostfix(String, DslPolicy)} -
     *       Fast infix-to-postfix conversion with DoS protection (length size only)</li>
     *   <li><strong>Phase 2</strong>: {@link BooleanSimplifier#validateAndSimplifyPostfix(List, DslPolicy)} -
     *       Boolean simplification but no validation of identifiers (validation are deferred to the step 3 for performance)</li>
     *   <li><strong>Phase 3</strong>: {@link PostfixConditionBuilder#build(List, Map, FilterContext)} -
     *       Single-pass Condition construction (executed during {@link FilterTree#generate(Map, FilterContext)} + automatic validation of identifiers</li>
     * </ol>
     *
     * <p>
     * <strong>Caching Strategy (v4.0.1+)</strong>:
     * </p>
     * <ul>
     *   <li>Cache Level: {@link FilterTree#generate(Map, FilterContext)} (deferred caching)</li>
     *   <li>Cache Key: Structural signature (filter definitions + simplified postfix)</li>
     *   <li>Cache Value: Generated {@link Condition} instances</li>
     *   <li>Cache Hit: Reuses cached Condition, skips Phase 3</li>
     *   <li>Cache Miss: Executes Phase 3, caches resulting Condition</li>
     *   <li>LRU Eviction: Least recently used entries evicted when cache full</li>
     *   <li>Structure-Based: Different DSL expressions with same filter structure share cache</li>
     * </ul>
     *
     * <p>
     * <strong>Performance Benefits</strong>:
     * </p>
     * <ul>
     *   <li>O(n) time complexity (single pass in each phase)</li>
     *   <li>Minimal object allocation (no Token objects, no intermediate AST)</li>
     *   <li>Deferred Condition construction (lazy evaluation during generate())</li>
     *   <li>Boolean simplification reduces cache key size and redundant logic</li>
     *   <li>Caching eliminates repeated parsing of identical expressions</li>
     * </ul>
     *
     * @param dslExpression the expression to parse
     * @return the parsed filter tree (lazy - builds Condition during generate())
     * @throws DSLSyntaxException if the expression is {@code null} or {@code empty} or contains syntax errors or exceeds limits
     */
    @Override
    public FilterTree parse(String dslExpression) throws DSLSyntaxException {
        if (dslExpression == null || dslExpression.isBlank()) {
            throw new DSLSyntaxException("DSL expression cannot be null or empty");
        }

        // Phase 1: Fast postfix conversion (DoS protection: length only)
        List<String> postfixTokens = FastPostfixConverter.toPostfix(dslExpression, dslPolicy);

        // Phase 2: Validation and simplification (DoS protection: token count, identifier validation)
        List<String> simplifiedPostfix = BooleanSimplifier.validateAndSimplifyPostfix(postfixTokens, dslPolicy);

        // Phase 3: Return lazy FilterTree that builds Condition on-demand
        return new PostfixFilterTree(simplifiedPostfix);
    }

    /**
     * Lazy FilterTree implementation that builds Condition from postfix tokens on-demand.
     * <p>
     * This class stores the simplified postfix expression and defers actual Condition
     * construction until {@link #generate(Map, FilterContext)} is called. This lazy evaluation
     * enables:
     * </p>
     * <ul>
     *   <li>Efficient filter tree caching (no Context dependency)</li>
     *   <li>Reusability across multiple contexts</li>
     *   <li>Minimal memory footprint (just token list)</li>
     * </ul>
     *
     * @since 4.0.1
     */
    private class PostfixFilterTree implements FilterTree {
        private final List<String> simplifiedPostfix;

        /**
         * Constructs a PostfixFilterTree from simplified postfix tokens.
         *
         * @param simplifiedPostfix simplified postfix token list (from {@link BooleanSimplifier})
         */
        PostfixFilterTree(List<String> simplifiedPostfix) {
            this.simplifiedPostfix = simplifiedPostfix;
        }

        @Override
        public String toString() {
            return "PostfixFilterTree[" + String.join(" ", simplifiedPostfix) + "]";
        }

        @Override
        public <P extends Enum<P> & PropertyReference> Condition generate(
                Map<String, FilterDefinition<P>> definitionsMap,
                FilterContext context) {

            Objects.requireNonNull(definitionsMap);
            Objects.requireNonNull(context);

            if (BasicDslParser.this.cache != null) {
                String cacheKey = StructuralNormalizer.normalizeStructure(definitionsMap, simplifiedPostfix);
                return cache.computeIfAbsent(cacheKey, expr -> getCondition(definitionsMap, context));
            } else {
                return getCondition(definitionsMap, context);
            }
        }

        private <P extends Enum<P> & PropertyReference> Condition getCondition(Map<String, FilterDefinition<P>> definitionsMap, FilterContext context) {
            final String upperCaseExpr = simplifiedPostfix.getFirst().toUpperCase();
            if (simplifiedPostfix.size() == 1 && ("AND".equals(upperCaseExpr) || "OR".equals(upperCaseExpr) || "NOT".equals(upperCaseExpr))) {

                var stream = definitionsMap.keySet().stream().map(key -> {
                    final var definition = definitionsMap.get(key);
                    return context.toCondition(key, definition.ref(), definition.op());
                });

                final Optional<Condition> combined = switch (upperCaseExpr) {
                    case "AND" -> stream.reduce(Condition::and);
                    case "OR"  -> stream.reduce(Condition::or);
                    case "NOT" -> stream.reduce(Condition::and).map(Condition::not);
                    default -> Optional.empty();
                };

                //noinspection OptionalGetWithoutIsPresent
                return combined.get();
            }

            return PostfixConditionBuilder.build(simplifiedPostfix, definitionsMap, context);
        }
    }
}
