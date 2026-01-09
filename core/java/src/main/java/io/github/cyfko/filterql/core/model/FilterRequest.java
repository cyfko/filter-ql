package io.github.cyfko.filterql.core.model;

import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;

import java.util.*;

/**
 * Immutable representation of a complete filter request combining filtering, sorting,
 * and pagination with optional DTO field projection.
 *
 * <p>
 * Encapsulates atomic filter definitions (property + operator + value), logical composition
 * expression (boolean DSL), pagination parameters, sorting configuration, and optional DTO
 * field projection. Supports arbitrarily complex filtering logic with type-safe components,
 * efficient pagination, and optimized data transfer.
 * </p>
 *
 * <h2>Core Components</h2>
 * <dl>
 *   <dt><strong>Filters ({@code filters})</strong></dt>
 *   <dd>Immutable map of filter keys to {@link FilterDefinition} instances</dd>
 *
 *   <dt><strong>Combination Expression ({@code combineWith})</strong></dt>
 *   <dd>Boolean DSL using {@code &} (AND), {@code |} (OR), {@code !} (NOT), {@code ( )} (grouping)</dd>
 *
 *   <dt><strong>DTO Projection ({@code projection})</strong></dt>
 *   <dd>Optional DTO field paths for response optimization (null = all fields)</dd>
 *
 *   <dt><strong>Pagination ({@code pagination})</strong></dt>
 *   <dd>Page number, page size, and optional sorting configuration</dd>
 * </dl>
 *
 * <h2>Optimized Two-Phase Validation</h2>
 * <ul>
 *   <li><strong>Phase 1 (Eager):</strong> Structural validation, immutability</li>
 *   <li><strong>Phase 2 (Deferred):</strong> DSL syntax, operator compatibility</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Fully immutable Java record with defensive copies and unmodifiable collections.</p>
 *
 * @param <P> Property reference enum type (extends {@code Enum} &amp; {@link PropertyReference})
 * @param filters Immutable map of filter keys to definitions
 * @param combineWith DSL expression (e.g., "(f1 &amp; f2) | f3", "AND", "OR")
 * @param projection Optional DTO field paths (null = all fields)
 * @param pagination Pagination configuration (null = no pagination)
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see FilterDefinition
 * @see PropertyReference
 * @see Pagination
 * @see Builder
 */
public record FilterRequest<P extends Enum<P> & PropertyReference>(
        Map<String, FilterDefinition<P>> filters,
        String combineWith,
        Set<String> projection,
        Pagination pagination) {

    /**
     * Canonical constructor with optimized two-phase validation.
     *
     * <p><strong>Phase 1 Validation Rules:</strong></p>
     * <ul>
     *   <li>Filters require non-null/non-blank combination expression</li>
     *   <li>Expression not allowed without filters</li>
     *   <li>Pagination validation deferred to usage</li>
     *   <li>Defensive immutability with unmodifiable collections</li>
     * </ul>
     *
     * @throws DSLSyntaxException if filter-expression consistency fails
     */
    public FilterRequest {
        if (filters != null && !filters.isEmpty()) {
            if (combineWith == null || combineWith.isBlank()) {
                throw new DSLSyntaxException("Filters combination expression is required.");
            }
        } else if (combineWith != null && !combineWith.isBlank()) {
            throw new DSLSyntaxException("Filters combination expression is not allowed when no filters.");
        }

        filters = filters == null ? Collections.emptyMap() :
                Collections.unmodifiableMap(new LinkedHashMap<>(filters));
        combineWith = combineWith == null ? "" : combineWith.trim();
        projection = projection == null ? null :
                Collections.unmodifiableSet(new LinkedHashSet<>(projection));
        // Pagination validation deferred - allows null for "all records" use case
    }

    /**
     * Returns {@code true} if this request contains filter definitions.
     *
     * @return {@code true} if filters present, {@code false} otherwise
     */
    public boolean hasFilters() {
        return !filters.isEmpty();
    }

    /**
     * Determines if full DTO projection is requested (all fields).
     *
     * @return {@code true} if projection null/empty (backward compatible)
     */
    public boolean hasProjection() {
        return projection == null || projection.isEmpty();
    }

    /**
     * Checks if pagination is configured.
     *
     * @return {@code true} if pagination present, {@code false} otherwise
     */
    public boolean hasPagination() {
        return pagination != null;
    }

    /**
     * Debug-friendly string representation excluding pagination details.
     *
     * @return Formatted string with filters, expression, projection
     */
    @Override
    public String toString() {
        return String.format("FilterRequest{filters=%s, combineWith='%s', projection=%s, pagination=%s}",
                filters, combineWith, projection, pagination);
    }

    /**
     * Factory method creating type-safe {@link Builder} instance.
     *
     * <p>Recommended fluent API entry point supporting DSL shorthands and pagination.</p>
     *
     * @param <R> Property enum type
     * @return New builder instance
     */
    public static <R extends Enum<R> & PropertyReference> Builder<R> builder() {
        return new Builder<>();
    }

    /**
     * Fluent builder supporting filter, projection, and pagination configuration.
     *
     * @param <P> Property enum type (extends {@code Enum} &amp; {@link PropertyReference})
     */
    public static class Builder<P extends Enum<P> & PropertyReference> {
        private final Map<String, FilterDefinition<P>> _filters = new HashMap<>();
        private String _combineWith;
        private Set<String> _projection;
        private Pagination _pagination;

        public Builder() {}

        /**
         * Adds named filter definition.
         *
         * @param name Filter key for DSL reference
         * @param definition Complete filter definition
         * @return This builder
         */
        public Builder<P> filter(String name, FilterDefinition<P> definition) {
            this._filters.put(name, definition);
            return this;
        }

        /**
         * Adds named filter with string operator.
         *
         * @param name Filter identifier
         * @param ref Property reference
         * @param op Operator string
         * @param value Filter value
         * @return This builder
         */
        public Builder<P> filter(String name, P ref, String op, Object value) {
            this._filters.put(name, new FilterDefinition<>(ref, op, value));
            return this;
        }

        /**
         * Adds named filter with typed operator.
         *
         * @param name Filter identifier
         * @param ref Property reference
         * @param op Typed operator
         * @param value Filter value
         * @return This builder
         */
        public Builder<P> filter(String name, P ref, Op op, Object value) {
            this._filters.put(name, new FilterDefinition<>(ref, op, value));
            return this;
        }

        /**
         * Bulk-adds filters from map.
         *
         * @param filters Filter definitions map
         * @return This builder
         */
        public Builder<P> filters(Map<String, FilterDefinition<P>> filters) {
            if (filters != null) {
                this._filters.putAll(filters);
            }
            return this;
        }

        /**
         * Sets boolean DSL combination expression.
         *
         * <p>Supports: "f1 &amp; f2", "AND", "OR", "NOT", complex expressions</p>
         *
         * @param expression DSL expression
         * @return This builder
         */
        public Builder<P> combineWith(String expression) {
            this._combineWith = expression;
            return this;
        }

        /**
         * Sets DTO field projection from set.
         *
         * @param projection DTO field paths
         * @return This builder
         */
        public Builder<P> projection(Set<String> projection) {
            this._projection = projection;
            return this;
        }

        /**
         * Sets DTO projection using varargs.
         *
         * @param fields DTO field paths (e.g., "name", "address.city")
         * @return This builder
         *
         * @example <pre>{@code .projection("name", "email", "address.city.name")}</pre>
         */
        public Builder<P> projection(String... fields) {
            if (fields != null && fields.length > 0) {
                this._projection = new LinkedHashSet<>(Arrays.asList(fields));
            }
            return this;
        }

        /**
         * Configures pagination with page number and size (no sorting).
         *
         * @param pageNumber 1-based page number
         * @param pageSize Number of records per page
         * @return This builder
         */
        public Builder<P> pagination(int pageNumber, int pageSize) {
            this._pagination = new Pagination(pageNumber, pageSize, null);
            return this;
        }

        /**
         * Configures pagination with primary sort and additional sort fields.
         *
         * <p>Additional fields provided as alternating field-direction pairs:
         * {@code pagination(1, 20, "name", "ASC", "createdAt", "DESC")}</p>
         *
         * @param pageNumber 1-based page number
         * @param pageSize Records per page
         * @param sortField Primary sort field
         * @param sortDirection Primary sort direction ("ASC"|"DESC")
         * @param sortFields Additional sort field-direction pairs
         * @return This builder
         *
         * @example <pre>{@code
         * .pagination(1, 20, "name", "ASC", "createdAt", "DESC")
         * }</pre>
         */
        public Builder<P> pagination(int pageNumber, int pageSize,
                                     String sortField, String sortDirection,
                                     String... sortFields) {
            List<SortBy> sort = new ArrayList<>();
            sort.add(new SortBy(sortField, sortDirection));

            for (int i = 1; i < sortFields.length; i += 2) {
                sort.add(new SortBy(sortFields[i-1], sortFields[i]));
            }

            this._pagination = new Pagination(pageNumber, pageSize, sort);
            return this;
        }

        /**
         * Builds immutable {@link FilterRequest} triggering canonical validation.
         *
         * @return New validated FilterRequest instance
         */
        public FilterRequest<P> build() {
            return new FilterRequest<>(_filters.isEmpty() ?  null : _filters, _combineWith, _projection, _pagination);
        }
    }
}
