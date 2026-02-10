package io.github.cyfko.filterql.spring.controller;

import java.util.List;

/**
 * Describes the filterable schema of an entity exposed via the FilterQL REST API.
 * <p>
 * Used to communicate the available filterable fields and their metadata to clients,
 * enabling dynamic UI generation and API documentation.
 * </p>
 *
 * <h2>Usage Context</h2>
 * <ul>
 *   <li>Returned by REST endpoints to describe entity filter capabilities</li>
 *   <li>Supports dynamic filter UI construction and validation</li>
 *   <li>Links entity name to its filterable fields</li>
 * </ul>
 *
 * @param entityName logical name of the entity (as exposed via API)
 * @param fields list of filterable fields and their metadata
 *
 * @author cyfko
 * @since 1.0
 */
public record EntityFilterSchema(
    String entityName,
    List<FilterableField> fields
) { }