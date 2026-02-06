package io.github.cyfko.filterql.core.cache;

import io.github.cyfko.filterql.core.model.FilterRequest;
import io.github.cyfko.filterql.core.api.PropertyReference;

/**
 * Strategy interface for normalizing DSL expressions to generate cache keys.
 * <p>
 * Normalization transforms a {@link FilterRequest} into a canonical string representation
 * that can be used as a cache key. Different normalization strategies allow for different
 * levels of cache granularity:
 * </p>
 * <ul>
 *   <li><strong>Structural Normalization</strong>: Ignores filter values, caches based on
 *       property references and operators only. High cache hit rate.</li>
 *   <li><strong>Value-Aware Normalization</strong>: Includes filter values in the cache key.
 *       Lower hit rate but caches complete queries.</li>
 * </ul>
 *
 * <h2>Normalization Process</h2>
 * <p>A normalizer typically performs the following steps:</p>
 * <ol>
 *   <li>Parse the DSL expression to extract filter identifiers</li>
 *   <li>Extract structural information (property references, operators, optionally values)</li>
 *   <li>Generate a canonical representation independent of filter names</li>
 *   <li>Return a deterministic string suitable as a cache key</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * DslNormalizer normalizer = new StructuralNormalizer();
 * String cacheKey = normalizer.normalize(filterRequest);
 *
 * // Use cache key for caching
 * Condition cached = cache.get(cacheKey);
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 * @see StructuralNormalizer
 * @see ValueAwareNormalizer
 */
@FunctionalInterface
public interface DslNormalizer {

    /**
     * Normalizes a filter request into a canonical cache key.
     * <p>
     * The returned string should be deterministic: identical logical structures
     * (according to the normalization strategy) must produce identical keys.
     * </p>
     *
     * @param request the filter request to normalize
     * @param <P> the property reference enum type
     * @return a normalized string suitable as a cache key
     * @throws IllegalArgumentException if the request is null or invalid
     */
    <P extends Enum<P> & PropertyReference> String normalize(FilterRequest<P> request);
}
