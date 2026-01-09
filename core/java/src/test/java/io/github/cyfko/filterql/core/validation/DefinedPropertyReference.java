package io.github.cyfko.filterql.core.validation;

import java.util.Set;

/**
 * Test enum that implements PropertyRef for testing purposes.
 */
public enum DefinedPropertyReference implements PropertyReference {
    USER_NAME(String.class, Set.of(Op.EQ, Op.MATCHES, Op.IN)),
    USER_AGE(Integer.class, Set.of(Op.EQ, Op.GT, Op.LT, Op.RANGE)),
    USER_EMAIL(String.class, Set.of(Op.EQ, Op.MATCHES, Op.NOT_NULL)),
    USER_STATUS(String.class, Set.of(Op.EQ, Op.NE, Op.IN));

    private final Class<?> type;
    private final Set<Op> supportedOperators;

    DefinedPropertyReference(Class<?> type, Set<Op> supportedOperators) {
        this.type = type;
        this.supportedOperators = Set.copyOf(supportedOperators);
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public Set<Op> getSupportedOperators() {
        return supportedOperators;
    }

    @Override
    public Class<?> getEntityType() {
        return null;
    }
}
