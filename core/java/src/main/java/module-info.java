module io.github.cyfko.filterql.core {
    requires jakarta.persistence;
    requires java.logging;

    exports io.github.cyfko.filterql.core;
    exports io.github.cyfko.filterql.core.api;
    exports io.github.cyfko.filterql.core.config;
    exports io.github.cyfko.filterql.core.exception;
    exports io.github.cyfko.filterql.core.model;
    exports io.github.cyfko.filterql.core.projection;
    exports io.github.cyfko.filterql.core.spi;
    exports io.github.cyfko.filterql.core.utils;
}