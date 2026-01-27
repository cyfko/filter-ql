package io.github.cyfko.filterql.jpa.projection;

public record Indexer(int index, boolean isCollection) {
    public static final Indexer NONE = new Indexer(-1, false);
}