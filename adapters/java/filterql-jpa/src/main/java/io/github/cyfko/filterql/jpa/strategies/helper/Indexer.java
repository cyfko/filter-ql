package io.github.cyfko.filterql.jpa.strategies.helper;

/**
 * Represents an index lookup result for a field in a {@link FieldSchema}.
 * <p>
 * Used to return both the index position and type information (whether it's a
 * collection)
 * from schema lookups. The special constant {@link #NONE} indicates a field was
 * not found.
 * </p>
 *
 * @param index        the index of the field in the schema arrays, or -1 if not
 *                     found
 * @param isCollection true if the field represents a collection slot
 * @author Frank KOSSI
 * @since 2.0.0
 */
public record Indexer(int index, boolean isCollection) {

    /**
     * Sentinel value indicating that the field was not found in the schema.
     */
    public static final Indexer NONE = new Indexer(-1, false);
}