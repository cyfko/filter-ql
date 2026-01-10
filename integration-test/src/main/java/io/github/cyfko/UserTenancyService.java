package io.github.cyfko;

import io.github.cyfko.filterql.core.spi.PredicateResolver;
import io.github.cyfko.filterql.core.validation.Op;
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
    public PredicateResolver<Person> isWithinCurrentOrg(String op, Object[] args) {
        // In real scenario, this would check current user's organization
        return (root, query, cb) -> {
            Boolean hasOrg = args.length > 0 ? (Boolean) args[0] : false;
            if (Boolean.TRUE.equals(hasOrg)) {
                return cb.isNotNull(root.get("email"));
            } else {
                return cb.isNull(root.get("email"));
            }
        };
    }
}
