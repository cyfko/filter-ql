package io.github.cyfko;

import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.api.Op;
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
}
