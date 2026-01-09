package io.github.cyfko.filterql.core.config;

import java.util.Objects;

/**
 * Central configuration object aggregating low-level behavioural strategies for FilterQL processing.
 * <p>
 * Initial version only exposes {@link NullValuePolicy}. Future iterations will extend this with additional
 * strategy enums (string case handling, enum match mode, empty collection policy, wildcard strategy, temporal
 * parsing profile, etc.). A builder is provided to keep construction fluent and forward compatible.
 * </p>
 */
public final class FilterConfig {

    private final NullValuePolicy nullValuePolicy;
    private final StringCaseStrategy stringCaseStrategy;
    private final EnumMatchMode enumMatchMode;

    private FilterConfig(Builder builder) {
        this.nullValuePolicy = builder.nullValuePolicy;
        this.stringCaseStrategy = builder.stringCaseStrategy;
        this.enumMatchMode = builder.enumMatchMode;
    }

    public static Builder builder() { return new Builder(); }

    public NullValuePolicy getNullValuePolicy() { return nullValuePolicy; }
    public StringCaseStrategy getStringCaseStrategy() { return stringCaseStrategy; }
    public EnumMatchMode getEnumMatchMode() { return enumMatchMode; }

    /**
     * Builder for {@link FilterConfig}. All future strategy knobs should be added here with sensible defaults.
     */
    public static final class Builder {
        private NullValuePolicy nullValuePolicy = NullValuePolicy.STRICT_EXCEPTION; // default
        private StringCaseStrategy stringCaseStrategy = StringCaseStrategy.LOWER; // default to lowercasing for LIKE
        private EnumMatchMode enumMatchMode = EnumMatchMode.CASE_INSENSITIVE; // default

        public Builder nullValuePolicy(NullValuePolicy policy) {
            this.nullValuePolicy = Objects.requireNonNull(policy, "nullValuePolicy");
            return this;
        }

        public Builder stringCaseStrategy(StringCaseStrategy strategy) {
            this.stringCaseStrategy = Objects.requireNonNull(strategy, "stringCaseStrategy");
            return this;
        }

        public Builder enumMatchMode(EnumMatchMode mode) {
            this.enumMatchMode = Objects.requireNonNull(mode, "enumMatchMode");
            return this;
        }

        public FilterConfig build() { return new FilterConfig(this); }
    }
}
