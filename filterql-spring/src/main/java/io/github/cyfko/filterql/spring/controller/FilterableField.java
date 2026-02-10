package io.github.cyfko.filterql.spring.controller;

import java.util.List;

/**
 * Describes a single filterable field of an entity for FilterQL API schema exposure.
 * <p>
 * Used to communicate field-level metadata to clients, including the field name, description,
 * and supported operators, enabling dynamic filter UI construction and validation.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Returned as part of {@link EntityFilterSchema} in REST API responses</li>
 *   <li>Supports documentation and dynamic filter UI generation</li>
 *   <li>Lists allowed operators for the field</li>
 * </ul>
 *
 * @param name logical name of the field (as exposed via API)
 * @param description human-readable description of the field
 * @param operators list of supported operator names for this field
 *
 * @author cyfko
 * @since 1.0
 */
public record FilterableField(
    String name,
    String description,
    List<String> operators
) { }