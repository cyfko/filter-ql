module io.github.cyfko.filterql.jpa {
    requires filterql.core;
    requires projection.metamodel.processor;
    requires jakarta.persistence;

    exports io.github.cyfko.filterql.jpa.exception;
    exports io.github.cyfko.filterql.jpa.spi;
    exports io.github.cyfko.filterql.jpa.strategies;
    exports io.github.cyfko.filterql.jpa;
}