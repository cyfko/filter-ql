package io.github.cyfko.filterql.jpa.strategies.helper;

public record Indexer(int index, boolean isCollection) {
    public static final Indexer NONE = new Indexer(-1, false);
}