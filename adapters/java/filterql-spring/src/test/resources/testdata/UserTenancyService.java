package io.github.cyfko.example;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.jpa.mappings.PredicateResolverMapping;
import io.github.cyfko.filterql.spring.ExposedAs;
import org.springframework.stereotype.Component;

/**
 * UserTenancyService - Service providing instance-based virtual field resolvers
 */
@Component
public class UserTenancyService {

    /**
     * Virtual field to filter users by organization membership
     */
    @ExposedAs(
            value = "HAS_ORG",
            operators = {Op.EQ}
    )
    public PredicateResolverMapping<Person> isWithinCurrentOrg() {
        // In real scenario, this would check current user's organization
        return (root, query, cb, params) -> {
            Boolean hasOrg = (Boolean) params[0];
            if (Boolean.TRUE.equals(hasOrg)) {
                return cb.isNotNull(root.get("email"));
            } else {
                return cb.isNull(root.get("email"));
            }
        };
    }
}
