package io.github.cyfko;

import io.github.cyfko.filterql.jpa.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
import io.github.cyfko.filterql.spring.ExposedAs;
import jakarta.persistence.criteria.Predicate;

/**
 * VirtualResolverConfig - Defines static virtual field resolvers for Person entity
 */
public class VirtualFields {

    /**
     * Virtual field to filter admin users
     */
    @ExposedAs(
            value = "IS_ADMIN",
            operators = {Op.EQ}
    )
    public static PredicateResolver<Person> isAdminUser(String op, Object[] args) {
        return (root, query, cb) -> {
            Boolean isAdmin = args.length > 0 ? (Boolean) args[0] : false;
            if (Boolean.TRUE.equals(isAdmin)) {
                // Filter for admin users
                return cb.equal(root.get("username"), "admin");
            } else {
                // Filter for non-admin users
                return cb.notEqual(root.get("username"), "admin");
            }
        };
    }

    /**
     * Virtual field: Full name (static method)
     * Searches in both first name and last name fields.
     */
    @ExposedAs(
            value = "FULL_NAME",
            operators = {Op.MATCHES}
    )
    public static PredicateResolver<Person> fullNameMatches(String op, Object[] args) {
        return (root, query, cb) -> {
            if (args.length == 0) return cb.conjunction();

            String searchTerm = (String) args[0];
            String pattern = "%" + searchTerm + "%";
            Predicate firstName = cb.like(root.get("firstName"), pattern);
            Predicate lastName = cb.like(root.get("lastName"), pattern);
            return cb.or(firstName, lastName);
        };
    }

    /**
     * Virtual field: Full name (static method)
     * Searches in an area defined by its WKB geom.
     */
    @ExposedAs(
            value = "WITHIN_GEOMETRY",
            operators = {Op.MATCHES}
    )
    public static PredicateResolver<Address> addressInGeometryArea(String op, Object[] args) {
        return (root, query, cb) -> cb.conjunction();
    }
}
