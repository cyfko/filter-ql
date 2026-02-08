module io.github.cyfko.filterql.jpa {
    requires io.github.cyfko.filterql.core;
    requires io.github.cyfko.jpametamodel;
    requires jakarta.persistence;

    exports io.github.cyfko.filterql.jpa.spi;
    exports io.github.cyfko.filterql.jpa.strategies;
    exports io.github.cyfko.filterql.jpa.exception;
    exports io.github.cyfko.filterql.jpa.utils;
    exports io.github.cyfko.filterql.jpa;

    opens io.github.cyfko.jpametamodel.providers.impl to io.github.cyfko.jpametamodel;
}