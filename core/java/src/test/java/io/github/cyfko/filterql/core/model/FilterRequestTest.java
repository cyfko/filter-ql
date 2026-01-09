package io.github.cyfko.filterql.core.model;

import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.exception.FilterValidationException;
import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.core.validation.DefinedPropertyReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class FilterRequestTest {

    @Test
    @DisplayName("Should create FilterRequest with filters and combination")
    void shouldCreateFilterRequestWithFiltersAndCombination() {
        // Given
        Map<String, FilterDefinition<DefinedPropertyReference>> filters = new HashMap<>();
        filters.put("filter1", new FilterDefinition<>(DefinedPropertyReference.USER_NAME, Op.EQ, "value1"));
        filters.put("filter2", new FilterDefinition<>(DefinedPropertyReference.USER_AGE, Op.GT, 10));
        String combineWith = "AND";

        // When
        FilterRequest<DefinedPropertyReference> filterRequest = new FilterRequest<>(filters, combineWith,null, null);

        // Then
        assertEquals(filters, filterRequest.filters());
        assertEquals(combineWith, filterRequest.combineWith());
        assertEquals(2, filterRequest.filters().size());
    }

    @Test
    @DisplayName("Should not create FilterRequest with empty filters given any kind of combination")
    void shouldNotCreateFilterRequestWithEmptyFiltersGivenAnyCombination() {
        // Given
        Map<String, FilterDefinition<DefinedPropertyReference>> filters = new HashMap<>();
        String combineWith = "x";

        // When
        DSLSyntaxException exception = assertThrows(DSLSyntaxException.class, () -> new FilterRequest<>(filters, combineWith,null, null));

        // Then
        assertTrue(exception.getMessage().contains("Filters combination expression is not allowed when no filters."));
    }

    @Test
    @DisplayName("Should create FilterRequest with empty filters given no combination")
    void shouldCreateFilterRequestWithEmptyFilters() {
        // Given
        Map<String, FilterDefinition<DefinedPropertyReference>> filters = new HashMap<>();

        // When
        var req = new FilterRequest<>(filters, null,null, null);

        // Then
        assertFalse(req.hasFilters());
    }

    @Test
    @DisplayName("Should create FilterRequest with null filters given no combination")
    void shouldCreateFilterRequestWithNullFilters() {
        // When
        var req = new FilterRequest<>(null, null,null, null);

        // Then
        assertFalse(req.hasFilters());
    }

    @Test
    @DisplayName("Should handle complex filter scenarios")
    void shouldHandleComplexFilterScenarios() {
        // Given
        Map<String, FilterDefinition<DefinedPropertyReference>> filters = new HashMap<>();
        filters.put("nameFilter", new FilterDefinition<>(DefinedPropertyReference.USER_NAME, Op.MATCHES, "John%"));
        filters.put("ageFilter", new FilterDefinition<>(DefinedPropertyReference.USER_AGE, Op.RANGE, List.of(18, 65)));
        filters.put("statusFilter", new FilterDefinition<>(DefinedPropertyReference.USER_STATUS, Op.IN, List.of("ACTIVE", "PENDING")));
        filters.put("nullFilter", new FilterDefinition<>(DefinedPropertyReference.USER_EMAIL, Op.NOT_NULL, null));

        // When
        FilterRequest<DefinedPropertyReference> filterRequest = new FilterRequest<>(filters, "AND", null, null);

        // Then
        assertEquals(4, filterRequest.filters().size());
        assertTrue(filterRequest.filters().containsKey("nameFilter"));
        assertTrue(filterRequest.filters().containsKey("ageFilter"));
        assertTrue(filterRequest.filters().containsKey("statusFilter"));
        assertTrue(filterRequest.filters().containsKey("nullFilter"));
        assertEquals("AND", filterRequest.combineWith());
    }

    @Test
    @DisplayName("Should handle toString method")
    void shouldHandleToStringMethod() {
        // Given
        Map<String, FilterDefinition<DefinedPropertyReference>> filters = new HashMap<>();
        filters.put("test", new FilterDefinition<>(DefinedPropertyReference.USER_NAME, Op.EQ, "value"));
        FilterRequest<DefinedPropertyReference> filterRequest = new FilterRequest<>(filters, "AND", null, null);

        // When
        String result = filterRequest.toString();

        // Then
        assertNotNull(result);
        assertTrue(result.contains("FilterRequest"));
    }

    @Test
    void shouldThrowIfFiltersPresentButNoCombineWith() {
        var builder = FilterRequest.<DefinedPropertyReference>builder()
                .filter("f1", DefinedPropertyReference.USER_NAME, "EQ", "Alice");

        DSLSyntaxException ex = assertThrows(DSLSyntaxException.class, builder::build);
        assertEquals("Filters combination expression is required.", ex.getMessage());
    }

    @Test
    void shouldThrowIfCombineWithPresentButNoFilters() {
        var builder = FilterRequest.<DefinedPropertyReference>builder()
                .combineWith("f1 & f2");

        DSLSyntaxException ex = assertThrows(DSLSyntaxException.class, builder::build);
        assertEquals("Filters combination expression is not allowed when no filters.", ex.getMessage());
    }

    @Test
    void shouldBuildValidRequestWithFiltersAndCombineWith() {
        var request = FilterRequest.<DefinedPropertyReference>builder()
                .filter("f1", DefinedPropertyReference.USER_NAME, "EQ", "Alice")
                .combineWith("f1")
                .build();

        assertTrue(request.hasFilters());
        assertEquals("f1", request.combineWith());
        assertEquals(1, request.filters().size());
    }

    @Test
    void shouldBuildValidRequestWithoutFiltersOrCombineWith() {
        var request = FilterRequest.<DefinedPropertyReference>builder().build();

        assertFalse(request.hasFilters());
        assertEquals("", request.combineWith());
    }

    @Test
    void shouldTreatNullProjectionAsFullProjection() {
        var request = FilterRequest.<DefinedPropertyReference>builder().build();
        assertTrue(request.hasProjection());
    }

    @Test
    void shouldTreatEmptyProjectionAsFullProjection() {
        var request = FilterRequest.<DefinedPropertyReference>builder()
                .projection(Set.of())
                .build();
        assertTrue(request.hasProjection());
    }

    @Test
    void shouldDetectExplicitProjection() {
        var request = FilterRequest.<DefinedPropertyReference>builder()
                .projection("name", "email")
                .build();
        assertFalse(request.hasProjection());
        assertEquals(Set.of("name", "email"), request.projection());
    }

    @Test
    void shouldDetectPaginationPresence() {
        var request = FilterRequest.<DefinedPropertyReference>builder()
                .pagination(1, 10)
                .build();
        assertTrue(request.hasPagination());
        assertEquals(1, request.pagination().page());
        assertEquals(10, request.pagination().size());
    }

    @Test
    void shouldAllowNullPagination() {
        var request = FilterRequest.<DefinedPropertyReference>builder().build();
        assertFalse(request.hasPagination());
    }

    @Test
    void shouldBuildPaginationWithMultipleSortFields() {
        var request = FilterRequest.<DefinedPropertyReference>builder()
                .pagination(2, 25, "name", "ASC", "createdAt", "DESC", "score", "ASC")
                .build();

        assertTrue(request.hasPagination());

        Pagination pagination = request.pagination();
        assertEquals(2, pagination.page());
        assertEquals(25, pagination.size());

        List<SortBy> sort = pagination.sort();
        assertNotNull(sort);
        assertEquals(3, sort.size());

        assertEquals("name", sort.get(0).field());
        assertEquals("asc", sort.get(0).direction());

        assertEquals("createdAt", sort.get(1).field());
        assertEquals("desc", sort.get(1).direction());

        assertEquals("score", sort.get(2).field());
        assertEquals("asc", sort.get(2).direction());
    }

    @Test
    void shouldIgnoreIncompleteSortPairsInPagination() {
        var request = FilterRequest.<DefinedPropertyReference>builder()
                .pagination(1, 10, "name", "ASC", "createdAt") // "createdAt" sans direction
                .build();

        Pagination pagination = request.pagination();
        assertNotNull(pagination);
        assertEquals(1, pagination.page());
        assertEquals(10, pagination.size());

        List<SortBy> sort = pagination.sort();
        assertNotNull(sort);
        assertEquals(1, sort.size()); // Seule la paire complète "name" + "ASC" est conservée

        assertEquals("name", sort.getFirst().field());
        assertEquals("asc", sort.getFirst().direction());
    }

}
