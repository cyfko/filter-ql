package io.github.cyfko.example;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping;
import io.github.cyfko.filterql.spring.ExposedAs;

/**
 * VirtualResolverConfig - Defines static virtual field resolvers for Person entity
 */
public class VirtualResolverConfig {

    /**
     * Virtual field to filter admin users
     */
    @ExposedAs(
            value = "IS_ADMIN",
            operators = {Op.EQ}
    )
    public static PredicateResolverMapping<Person> isAdminUser() {
        return (root, query, cb, params) -> {
            Boolean isAdmin = (Boolean) params[0];
            if (Boolean.TRUE.equals(isAdmin)) {
                // Filter for admin users
                return cb.equal(root.get("username"), "admin");
            } else {
                // Filter for non-admin users
                return cb.notEqual(root.get("username"), "admin");
            }
        };
    }
}
