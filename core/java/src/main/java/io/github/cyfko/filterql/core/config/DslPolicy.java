package io.github.cyfko.filterql.core.config;

import java.util.regex.Pattern;

/**
 * Configuration for DSL Parser complexity limits for DoS protection.
 *
 * <h2>Configurable Limits</h2>
 * <ul>
 *   <li><strong>maxExpressionLength</strong>: Maximum character length of the expression (default: 5000)</li>
 * </ul>
 *
 * <h2>Preset Configurations</h2>
 * <pre>{@code
 * // Default (balanced for most use cases)
 * DslPolicy config = DslPolicy.defaults();
 *
 * // Strict (for public APIs with untrusted input)
 * DslPolicy config = DslPolicy.strict();
 *
 * // Relaxed (for internal trusted systems)
 * DslPolicy config = DslPolicy.relaxed();
 *
 * // Custom
 * DslPolicy config = DslPolicy.builder()
 *     .maxExpressionLength(10000)
 *     .build();
 * }</pre>
 *
 * @param maxExpressionLength maximum character length of expression string
 * @author Frank KOSSI
 * @since 4.0.0
 */
public record DslPolicy(
    String policyName,
    int maxExpressionLength,
    Pattern identifierPattern,
    boolean validateIdentifiers
) {

    /**
     * Canonical constructor with validation.
     *
     * @throws IllegalArgumentException if any size is invalid
     */
    public DslPolicy {
        if (policyName == null || policyName.isBlank()) {
            throw new IllegalArgumentException("Policy name is required");
        }
        if (maxExpressionLength <= 0) {
            throw new IllegalArgumentException("maxExpressionLength must be positive, got: " + maxExpressionLength);
        }
        if (identifierPattern == null) {
            throw new IllegalArgumentException("identifierPattern is required");
        }
    }

    /**
     * Default configuration for balanced protection and usability.
     * <p>
     * Suitable for most production use cases with moderate complexity requirements.
     * Identifier validation is DISABLED by default for better performance (validation
     * deferred to PostfixConditionBuilder during actual usage).
     * </p>
     * <ul>
     *   <li>Max Expression Length: 5000 characters</li>
     *   <li>Identifier Validation: DISABLED (deferred)</li>
     * </ul>
     *
     * @return default configuration
     */
    public static DslPolicy defaults() {
        return new DslPolicy(PolicyName.DEFAULT_POLICY.name(), 5000, PatternConfig.SIMPLE_IDENTIFIER_PATTERN, false);
    }

    /**
     * Strict configuration for public APIs and untrusted input.
     * <p>
     * Recommended for APIs exposed to external clients or untrusted sources.
     * Identifier validation is ENABLED for additional security.
     * </p>
     * <ul>
     *   <li>Max Expression Length: 1000 characters</li>
     *   <li>Identifier Validation: ENABLED (strict mode)</li>
     * </ul>
     *
     * @return strict configuration
     */
    public static DslPolicy strict() {
        return new DslPolicy(PolicyName.STRICT_POLICY.name(), 1000, PatternConfig.SIMPLE_IDENTIFIER_PATTERN, true);
    }

    /**
     * Relaxed configuration for internal trusted systems.
     * <p>
     * Suitable for internal applications and trusted batch processes.
     * Identifier validation is DISABLED for maximum performance.
     * </p>
     * <ul>
     *   <li>Max Expression Length: 10000 characters</li>
     *   <li>Identifier Validation: DISABLED (trusted input)</li>
     * </ul>
     *
     * @return relaxed configuration
     */
    public static DslPolicy relaxed() {
        return new DslPolicy(PolicyName.RELAXED_POLICY.name(), 10000, PatternConfig.SIMPLE_IDENTIFIER_PATTERN, false);
    }

    /**
     * Creates a custom configuration with full control over all parameters.
     * <p>
     * Builder parameters are default initialized exactly as if created with the default mode.
     * </p>
     *
     * @return the {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder{
        private String _policyName = PolicyName.CUSTOM_POLICY.name();
        private int _maxExpressionLength = 5000;
        private Pattern _identifierPattern = PatternConfig.SIMPLE_IDENTIFIER_PATTERN;
        private boolean _validateIdentifiers = false; // Default: disabled for performance

        private Builder(){}

        public DslPolicy build(){
            return new DslPolicy(_policyName, _maxExpressionLength, _identifierPattern, _validateIdentifiers);
        }

        public Builder policyName(String policyName){ this._policyName = policyName; return this; }
        public Builder maxExpressionLength(int maxExpressionLength){ this._maxExpressionLength = maxExpressionLength; return this; }
        public Builder identifierPattern(Pattern identifierPattern){ this._identifierPattern = identifierPattern; return this; }
        public Builder validateIdentifiers(boolean validate){ this._validateIdentifiers = validate; return this; }
    }

    public enum PolicyName{
        DEFAULT_POLICY,
        STRICT_POLICY,
        RELAXED_POLICY,
        CUSTOM_POLICY
    }
}
