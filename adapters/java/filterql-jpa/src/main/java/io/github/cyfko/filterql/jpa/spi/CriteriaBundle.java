package io.github.cyfko.filterql.jpa.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Encapsulates the JPA Criteria API components required for query execution.
 * 
 * <p>
 * This record bundles together the essential elements produced during
 * predicate resolution, enabling seamless integration with JPA query execution.
 * </p>
 *
 * @param predicate the resolved JPA predicate for the WHERE clause
 * @param query     the criteria query being constructed
 * @param cb        the criteria builder used for predicate creation
 * @param root      the query root element
 * 
 * @author Frank KOSSI
 */
public record CriteriaBundle(Predicate predicate, CriteriaQuery<?> query, CriteriaBuilder cb, Root<?> root) {
}
