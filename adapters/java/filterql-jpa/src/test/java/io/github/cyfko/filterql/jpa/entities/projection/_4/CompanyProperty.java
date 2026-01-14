package io.github.cyfko.filterql.jpa.entities.projection._4;

import java.util.List;
import java.util.Set;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.PropertyReference;

/**
 * Property reference enum for Company entity filtering.
 */
public enum CompanyProperty implements PropertyReference {
    ID,
    NAME,
    COUNTRY,
    FOUNDED_YEAR,
    DEPARTMENTS;

    @Override
    public Class<?> getType() {
        return switch (this) {
            case ID -> Long.class;
            case NAME -> String.class;
            case COUNTRY -> String.class;
            case FOUNDED_YEAR -> Integer.class;
            case DEPARTMENTS -> List.class;
        };
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return switch (this) {
            case ID -> Set.of(Op.EQ, Op.NE);
            case NAME, COUNTRY -> Set.of(Op.EQ, Op.NE, Op.MATCHES, Op.NOT_MATCHES);
            case FOUNDED_YEAR -> Set.of(Op.EQ, Op.NE);
            case DEPARTMENTS -> Set.of(Op.IN, Op.NOT_IN);
            default -> throw new IllegalArgumentException("Unexpected value: " + this);
        };
    }

    @Override
    public Class<?> getEntityType() {
        return Company.class;
    }
}
