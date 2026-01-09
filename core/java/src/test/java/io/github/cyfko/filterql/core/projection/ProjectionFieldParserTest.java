package io.github.cyfko.filterql.core.projection;

import io.github.cyfko.filterql.core.exception.ProjectionDefinitionException;
import io.github.cyfko.filterql.core.model.Pagination;
import io.github.cyfko.filterql.core.model.SortBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for {@link ProjectionFieldParser}.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>✅ Simple field parsing</li>
 *   <li>✅ Nested field parsing</li>
 *   <li>✅ Multi-field projection parsing</li>
 *   <li>✅ Hierarchical pagination options</li>
 *   <li>✅ Sort field parsing with multiple columns</li>
 *   <li>✅ Input validation and error cases</li>
 *   <li>✅ Edge cases and boundary conditions</li>
 *   <li>✅ Conflict detection</li>
 *   <li>✅ Thread safety (immutability)</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @version 2.0
 */
@DisplayName("ProjectionFieldParser Tests")
class ProjectionFieldParserTest {

    @Nested
    @DisplayName("Simple Field Parsing")
    class SimpleFieldParsingTests {

        @Test
        @DisplayName("Should parse simple field without prefix")
        void shouldParseSimpleField() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("name");

            assertEquals("", result.prefix());
            assertEquals(List.of("name"), result.fields());
            assertFalse(result.isCollection());
            assertEquals("name", result.getFullPath());
        }

        @Test
        @DisplayName("Should parse field with underscores")
        void shouldParseFieldWithUnderscores() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("first_name");

            assertEquals("", result.prefix());
            assertEquals(List.of("first_name"), result.fields());
        }

        @Test
        @DisplayName("Should parse field with hyphens")
        void shouldParseFieldWithHyphens() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("first-name");

            assertEquals("", result.prefix());
            assertEquals(List.of("first-name"), result.fields());
        }

        @Test
        @DisplayName("Should parse field with numbers")
        void shouldParseFieldWithNumbers() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("field123");

            assertEquals("", result.prefix());
            assertEquals(List.of("field123"), result.fields());
        }
    }

    @Nested
    @DisplayName("Nested Field Parsing")
    class NestedFieldParsingTests {

        @Test
        @DisplayName("Should parse two-level nested field")
        void shouldParseTwoLevelNestedField() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("address.city");

            assertEquals("address", result.prefix());
            assertEquals(List.of("city"), result.fields());
            assertTrue(result.isCollection());
            assertEquals("address.city", result.getFullPath());
        }

        @Test
        @DisplayName("Should parse three-level nested field")
        void shouldParseThreeLevelNestedField() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("address.city.name");

            assertEquals("address.city", result.prefix());
            assertEquals(List.of("name"), result.fields());
            assertTrue(result.isCollection());
            assertEquals("address.city.name", result.getFullPath());
        }

        @Test
        @DisplayName("Should parse deep nested field")
        void shouldParseDeepNestedField() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("user.profile.settings.preferences.theme");

            assertEquals("user.profile.settings.preferences", result.prefix());
            assertEquals(List.of("theme"), result.fields());
            assertEquals("user.profile.settings.preferences.theme", result.getFullPath());
        }
    }

    @Nested
    @DisplayName("Multi-Field Parsing")
    class MultiFieldParsingTests {

        @Test
        @DisplayName("Should parse multi-field with two fields")
        void shouldParseMultiFieldWithTwoFields() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("orders.items.name,price");

            assertEquals("orders.items", result.prefix());
            assertEquals(List.of("name", "price"), result.fields());
            assertTrue(result.isCollection());
            assertEquals("orders.items.name", result.getFullPath());
        }

        @Test
        @DisplayName("Should parse multi-field with three fields")
        void shouldParseMultiFieldWithThreeFields() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("orders.items.name,price,quantity");

            assertEquals("orders.items", result.prefix());
            assertEquals(List.of("name", "price", "quantity"), result.fields());
        }

        @Test
        @DisplayName("Should parse multi-field at root level")
        void shouldParseMultiFieldAtRootLevel() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("name,email,phone");

            assertEquals("", result.prefix());
            assertEquals(List.of("name", "email", "phone"), result.fields());
        }

        @Test
        @DisplayName("Should handle spaces around commas")
        void shouldHandleSpacesAroundCommas() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("user.name , email , phone");

            assertEquals("user", result.prefix());
            assertEquals(List.of("name", "email", "phone"), result.fields());
        }

        @Test
        @DisplayName("Should get all full paths for multi-field")
        void shouldGetAllFullPathsForMultiField() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("orders.items.name,price,quantity");

            List<String> allPaths = result.getAllFullPaths();
            assertEquals(
                    List.of("orders.items.name", "orders.items.price", "orders.items.quantity"),
                    allPaths
            );
        }
    }

    @Nested
    @DisplayName("Hierarchical Options Removal")
    class HierarchicalOptionsRemovalTests {

        @Test
        @DisplayName("Should remove single bracket option")
        void shouldRemoveSingleBracketOption() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("orders[size=5].items.name");

            assertEquals("orders.items", result.prefix());
            assertEquals(List.of("name"), result.fields());
        }

        @Test
        @DisplayName("Should remove multiple bracket options")
        void shouldRemoveMultipleBracketOptions() {
            ProjectionFieldParser.ProjectionField result =
                    ProjectionFieldParser.parse("orders[size=10].items[page=2,sort=name:asc].price");

            assertEquals("orders.items", result.prefix());
            assertEquals(List.of("price"), result.fields());
        }

        @Test
        @DisplayName("Should handle complex nested options")
        void shouldHandleComplexNestedOptions() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse(
                    "orders[size=10,page=0,sort=date:desc].items[size=5,sort=name:asc,price:desc].name,price"
            );

            assertEquals("orders.items", result.prefix());
            assertEquals(List.of("name", "price"), result.fields());
        }
    }

    @Nested
    @DisplayName("Collection Options Parsing")
    class CollectionOptionsParsingTests {

        @Test
        @DisplayName("Should parse size option")
        void shouldParseSizeOption() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=20].items.name")
            );

            assertTrue(options.containsKey("orders"));
            Pagination pagination = options.get("orders");
            assertEquals(20, pagination.size());
            assertEquals(0, pagination.page());
        }

        @Test
        @DisplayName("Should parse page and size options")
        void shouldParsePageAndSizeOptions() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=15,page=3].items.name")
            );

            Pagination pagination = options.get("orders");
            assertEquals(15, pagination.size());
            assertEquals(3, pagination.page());
        }

        @Test
        @DisplayName("Should parse sort option with single field")
        void shouldParseSortOptionWithSingleField() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[sort=date:desc].items.name")
            );

            Pagination pagination = options.get("orders");
            assertNotNull(pagination.sort());
            assertEquals(1, pagination.sort().size());

            SortBy sortField = pagination.sort().get(0);
            assertEquals("date", sortField.field());
            assertEquals("desc", sortField.direction());
        }

        @Test
        @DisplayName("Should parse sort option with multiple fields")
        void shouldParseSortOptionWithMultipleFields() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[sort=name:asc,price:desc,date:asc].items.name")
            );

            Pagination pagination = options.get("orders");
            assertNotNull(pagination.sort());
            assertEquals(3, pagination.sort().size());

            assertEquals("name", pagination.sort().get(0).field());
            assertEquals("asc", pagination.sort().get(0).direction());

            assertEquals("price", pagination.sort().get(1).field());
            assertEquals("desc", pagination.sort().get(1).direction());

            assertEquals("date", pagination.sort().get(2).field());
            assertEquals("asc", pagination.sort().get(2).direction());
        }

        @Test
        @DisplayName("Should parse complete options (size, page, sort)")
        void shouldParseCompleteOptions() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=25,page=5,sort=date:desc,amount:asc].items.name")
            );

            Pagination pagination = options.get("orders");
            assertEquals(25, pagination.size());
            assertEquals(5, pagination.page());
            assertEquals(2, pagination.sort().size());
        }

        @Test
        @DisplayName("Should parse hierarchical options")
        void shouldParseHierarchicalOptions() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=10].items[size=5,sort=name:asc].name")
            );

            assertTrue(options.containsKey("orders"));
            assertTrue(options.containsKey("orders.items"));

            Pagination ordersPagination = options.get("orders");
            assertEquals(10, ordersPagination.size());

            Pagination itemsPagination = options.get("orders.items");
            assertEquals(5, itemsPagination.size());
            assertNotNull(itemsPagination.sort());
        }

        @Test
        @DisplayName("Should handle empty projection set")
        void shouldHandleEmptyProjectionSet() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(Set.of());
            assertTrue(options.isEmpty());
        }

        @Test
        @DisplayName("Should handle null projection set")
        void shouldHandleNullProjectionSet() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(null);
            assertTrue(options.isEmpty());
        }

        @Test
        @DisplayName("Should merge compatible options from multiple fields")
        void shouldMergeCompatibleOptions() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of(
                            "orders[size=10].items.name",
                            "orders[size=10].items.price"
                    )
            );

            assertEquals(1, options.size());
            assertEquals(10, options.get("orders").size());
        }

        @Test
        @DisplayName("Should handle default sort direction")
        void shouldHandleDefaultSortDirection() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[sort=name].items.id")
            );

            Pagination pagination = options.get("orders");
            assertEquals("asc", pagination.sort().get(0).direction());
        }

        @Test
        @DisplayName("Should handle case-insensitive sort direction")
        void shouldHandleCaseInsensitiveSortDirection() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[sort=name:ASC,price:DESC].items.id")
            );

            Pagination pagination = options.get("orders");
            assertEquals("asc", pagination.sort().get(0).direction());
            assertEquals("desc", pagination.sort().get(1).direction());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("Should reject null or blank field specifications")
        void shouldRejectNullOrBlankFieldSpec(String invalidSpec) {
            assertThrows(IllegalArgumentException.class, () ->
                    ProjectionFieldParser.parse(invalidSpec)
            );
        }

        @Test
        @DisplayName("Should reject field with special characters")
        void shouldRejectFieldWithSpecialCharacters() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse("user.name@domain")
            );
        }

        @Test
        @DisplayName("Should reject field with spaces in name")
        void shouldRejectFieldWithSpacesInName() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse("user.first name")
            );
        }

        @Test
        @DisplayName("Should reject consecutive dots")
        void shouldRejectConsecutiveDots() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse("user..name")
            );
        }

        @Test
        @DisplayName("Should reject leading dot")
        void shouldRejectLeadingDot() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse(".user.name")
            );
        }

        @Test
        @DisplayName("Should reject trailing dot")
        void shouldRejectTrailingDot() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse("user.name.")
            );
        }

        @Test
        @DisplayName("Should ignore successive empty fields in multi-field spec")
        void shouldIgnoreEmptyFieldsInMultiField() {
            ProjectionFieldParser.ProjectionField parsedField = ProjectionFieldParser.parse("user.name,,,");
            assertEquals("user", parsedField.prefix());
            assertEquals("name", parsedField.fields().getFirst());
            assertEquals(1, parsedField.fields().size());
        }

        @Test
        @DisplayName("Should reject specification with only brackets")
        void shouldRejectOnlyBrackets() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse("[size=10]")
            );
        }

        @Test
        @DisplayName("Should reject negative page size")
        void shouldRejectNegativePageSize() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[size=-10].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject zero page size")
        void shouldRejectZeroPageSize() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[size=0].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject negative page number")
        void shouldRejectNegativePageNumber() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[page=-1].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject page size exceeding maximum")
        void shouldRejectPageSizeExceedingMaximum() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[size=20000].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject invalid sort direction")
        void shouldRejectInvalidSortDirection() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[sort=name:invalid].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject empty sort specification")
        void shouldRejectEmptySortSpecification() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[sort=].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject unknown option")
        void shouldRejectUnknownOption() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[unknown=value].items.name")
                    )
            );
        }

        @Test
        @DisplayName("Should reject conflicting pagination options")
        void shouldRejectConflictingPaginationOptions() {
            ProjectionDefinitionException exception = assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of(
                                    "orders[size=10].items.name",
                                    "orders[size=20].items.price"
                            )
                    )
            );
            assertTrue(exception.getMessage().contains("Conflicting"));
        }

        @Test
        @DisplayName("Should reject conflicting sort options")
        void shouldRejectConflictingSortOptions() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of(
                                    "orders[sort=name:asc].items.id",
                                    "orders[sort=name:desc].items.price"
                            )
                    )
            );
        }

        @Test
        @DisplayName("Should reject invalid numeric format")
        void shouldRejectInvalidNumericFormat() {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parseCollectionOptions(
                            Set.of("orders[size=abc].items.name")
                    )
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle single character field name")
        void shouldHandleSingleCharacterFieldName() {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse("a");
            assertEquals("", result.prefix());
            assertEquals(List.of("a"), result.fields());
        }

        @Test
        @DisplayName("Should handle very long field path")
        void shouldHandleVeryLongFieldPath() {
            String longPath = "a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z";
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse(longPath);

            assertEquals("a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y", result.prefix());
            assertEquals(List.of("z"), result.fields());
        }

        @Test
        @DisplayName("Should handle maximum allowed page size")
        void shouldHandleMaximumAllowedPageSize() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=10000].items.name")
            );

            assertEquals(10000, options.get("orders").size());
        }

        @Test
        @DisplayName("Should handle many multi-fields")
        void shouldHandleManyMultiFields() {
            StringBuilder fieldSpec = new StringBuilder("user");
            for (int i = 1; i <= 20; i++) {
                if (i > 1) fieldSpec.append(",");
                fieldSpec.append("field").append(i);
            }

            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse(fieldSpec.toString());
            assertEquals(20, result.fields().size());
        }

        @Test
        @DisplayName("Should handle spaces in options")
        void shouldHandleSpacesInOptions() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[ size = 10 , page = 2 ].items.name")
            );

            Pagination pagination = options.get("orders");
            assertEquals(10, pagination.size());
            assertEquals(2, pagination.page());
        }

        @Test
        @DisplayName("Should skip null entries in projection set")
        void shouldSkipNullEntriesInProjectionSet() {
            Set<String> projectionSet = new HashSet<>();
            projectionSet.add("orders[size=10].items.name");
            projectionSet.add(null);
            projectionSet.add("orders.items.price");

            assertDoesNotThrow(() ->
                    ProjectionFieldParser.parseCollectionOptions(projectionSet)
            );
        }

        @Test
        @DisplayName("Should skip blank entries in projection set")
        void shouldSkipBlankEntriesInProjectionSet() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=10].items.name", "  ", "")
            );

            assertEquals(1, options.size());
        }
    }

    @Nested
    @DisplayName("ProjectionField Record Tests")
    class ProjectionFieldRecordTests {

        @Test
        @DisplayName("Should create ProjectionField with valid parameters")
        void shouldCreateProjectionFieldWithValidParams() {
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("orders", List.of("id", "name"));

            assertEquals("orders", field.prefix());
            assertEquals(List.of("id", "name"), field.fields());
        }

        @Test
        @DisplayName("Should reject null prefix")
        void shouldRejectNullPrefix() {
            assertThrows(NullPointerException.class, () ->
                    new ProjectionFieldParser.ProjectionField(null, List.of("id"))
            );
        }

        @Test
        @DisplayName("Should reject null fields list")
        void shouldRejectNullFieldsList() {
            assertThrows(NullPointerException.class, () ->
                    new ProjectionFieldParser.ProjectionField("orders", null)
            );
        }

        @Test
        @DisplayName("Should reject empty fields list")
        void shouldRejectEmptyFieldsList() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ProjectionFieldParser.ProjectionField("orders", List.of())
            );
        }

        @Test
        @DisplayName("Should return immutable fields list")
        void shouldReturnImmutableFieldsList() {
            List<String> mutableList = new ArrayList<>(List.of("id", "name"));
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("orders", mutableList);

            assertThrows(UnsupportedOperationException.class, () ->
                    field.fields().add("email")
            );
        }

        @Test
        @DisplayName("Should detect collection for nested field")
        void shouldDetectCollectionForNestedField() {
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("orders.items", List.of("name"));

            assertTrue(field.isCollection());
        }

        @Test
        @DisplayName("Should not detect collection for simple field")
        void shouldNotDetectCollectionForSimpleField() {
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("", List.of("name"));

            assertFalse(field.isCollection());
        }

        @Test
        @DisplayName("Should format toString for single field")
        void shouldFormatToStringForSingleField() {
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("orders.items", List.of("name"));

            assertEquals("orders.items.name", field.toString());
        }

        @Test
        @DisplayName("Should format toString for multi-field")
        void shouldFormatToStringForMultiField() {
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("orders.items", List.of("name", "price"));

            assertEquals("orders.items.[name,price]", field.toString());
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Modifying input list should not affect ProjectionField")
        void modifyingInputListShouldNotAffectProjectionField() {
            List<String> inputList = new ArrayList<>(List.of("id", "name"));
            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("user", inputList);

            inputList.add("email");

            assertEquals(2, field.fields().size());
            assertEquals(List.of("id", "name"), field.fields());
        }

        @Test
        @DisplayName("Returned collection options map should be immutable")
        void returnedCollectionOptionsMapShouldBeImmutable() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=10].items.name")
            );

            assertThrows(UnsupportedOperationException.class, () ->
                    options.put("newKey", new Pagination(0, 10, null))
            );
        }

        @Test
        @DisplayName("ProjectionField should be defensively copied")
        void projectionFieldShouldBeDefensivelyCopied() {
            ArrayList<String> mutableList = new ArrayList<>();
            mutableList.add("id");

            ProjectionFieldParser.ProjectionField field =
                    new ProjectionFieldParser.ProjectionField("user", mutableList);

            mutableList.add("hacked");

            assertEquals(1, field.fields().size());
        }
    }

    @Nested
    @DisplayName("Complex Integration Tests")
    class ComplexIntegrationTests {

        @Test
        @DisplayName("Should handle complex real-world scenario")
        void shouldHandleComplexRealWorldScenario() {
            Set<String> projectionSet = Set.of(
                    "users[size=20,page=0,sort=createdAt:desc].orders[size=10,sort=orderDate:desc].items.productName,price,quantity",
                    "users[size=20,page=0,sort=createdAt:desc].profile.firstName,lastName,email",
                    "users[size=20,page=0,sort=createdAt:desc].address.street,city,country"
            );

            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(projectionSet);

            // Verify users pagination
            assertTrue(options.containsKey("users"));
            Pagination usersPagination = options.get("users");
            assertEquals(20, usersPagination.size());
            assertEquals(0, usersPagination.page());
            assertEquals(1, usersPagination.sort().size());
            assertEquals("createdAt", usersPagination.sort().get(0).field());
            assertEquals("desc", usersPagination.sort().get(0).direction());

            // Verify nested orders pagination
            assertTrue(options.containsKey("users.orders"));
            Pagination ordersPagination = options.get("users.orders");
            assertEquals(10, ordersPagination.size());
        }

        @Test
        @DisplayName("Should parse multiple independent collections")
        void shouldParseMultipleIndependentCollections() {
            Set<String> projectionSet = Set.of(
                    "orders[size=10,sort=date:desc].items.name",
                    "products[size=20,sort=price:asc].reviews.rating"
            );

            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(projectionSet);

            assertEquals(2, options.size());
            assertTrue(options.containsKey("orders"));
            assertTrue(options.containsKey("products"));
        }

        @Test
        @DisplayName("Should handle three-level hierarchy")
        void shouldHandleThreeLevelHierarchy() {
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("level1[size=10].level2[size=5].level3[size=3].field")
            );

            assertEquals(3, options.size());
            assertEquals(10, options.get("level1").size());
            assertEquals(5, options.get("level1.level2").size());
            assertEquals(3, options.get("level1.level2.level3").size());
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @MethodSource("provideValidFieldSpecs")
        @DisplayName("Should successfully parse valid field specifications")
        void shouldParseValidFieldSpecs(String fieldSpec, String expectedPrefix, List<String> expectedFields) {
            ProjectionFieldParser.ProjectionField result = ProjectionFieldParser.parse(fieldSpec);

            assertEquals(expectedPrefix, result.prefix());
            assertEquals(expectedFields, result.fields());
        }

        static Stream<Arguments> provideValidFieldSpecs() {
            return Stream.of(
                    Arguments.of("id", "", List.of("id")),
                    Arguments.of("user.name", "user", List.of("name")),
                    Arguments.of("user.profile.email", "user.profile", List.of("email")),
                    Arguments.of("orders.items.name,price", "orders.items", List.of("name", "price")),
                    Arguments.of("a,b,c", "", List.of("a", "b", "c")),
                    Arguments.of("field_123", "", List.of("field_123")),
                    Arguments.of("field-name", "", List.of("field-name"))
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "user..name",
                ".user.name",
                "user.name.",
                "user@domain.name",
                "user name",
                "[size=10]"
        })
        @DisplayName("Should reject invalid field specifications")
        void shouldRejectInvalidFieldSpecs(String invalidSpec) {
            assertThrows(ProjectionDefinitionException.class, () ->
                    ProjectionFieldParser.parse(invalidSpec)
            );
        }
    }

    @Nested
    @DisplayName("Field Distinction Tests - CRITICAL")
    class FieldDistinctionTests {

        @Test
        @DisplayName("Should distinguish simple 'name' from nested 'name.name'")
        void shouldDistinguishSimpleFromNestedSameName() {
            // Simple field "name"
            ProjectionFieldParser.ProjectionField simple = ProjectionFieldParser.parse("name");
            assertEquals("", simple.prefix(), "Simple field should have empty prefix");
            assertEquals(List.of("name"), simple.fields());
            assertEquals("name", simple.getFullPath());
            assertFalse(simple.isCollection(), "Simple field should not be a collection");
            assertTrue(simple.isSimpleField(), "Should be identified as simple field");

            // Nested field "name.name"
            ProjectionFieldParser.ProjectionField nested = ProjectionFieldParser.parse("name.name");
            assertEquals("name", nested.prefix(), "Nested field should have 'name' as prefix");
            assertEquals(List.of("name"), nested.fields());
            assertEquals("name.name", nested.getFullPath());
            assertTrue(nested.isCollection(), "Nested field should be a collection");
            assertFalse(nested.isSimpleField(), "Should not be identified as simple field");

            // They must be different!
            assertNotEquals(simple.prefix(), nested.prefix(), "Prefixes must differ");
            assertNotEquals(simple.getFullPath(), nested.getFullPath(), "Full paths must differ");
        }

        @Test
        @DisplayName("Should distinguish 'id' from 'user.id'")
        void shouldDistinguishRootFromNestedField() {
            ProjectionFieldParser.ProjectionField root = ProjectionFieldParser.parse("id");
            ProjectionFieldParser.ProjectionField nested = ProjectionFieldParser.parse("user.id");

            assertEquals("", root.prefix());
            assertEquals("user", nested.prefix());
            assertEquals("id", root.getFullPath());
            assertEquals("user.id", nested.getFullPath());
        }

        @Test
        @DisplayName("Should handle multiple nesting levels correctly")
        void shouldHandleMultipleNestingLevels() {
            ProjectionFieldParser.ProjectionField level0 = ProjectionFieldParser.parse("field");
            ProjectionFieldParser.ProjectionField level1 = ProjectionFieldParser.parse("a.field");
            ProjectionFieldParser.ProjectionField level2 = ProjectionFieldParser.parse("a.b.field");
            ProjectionFieldParser.ProjectionField level3 = ProjectionFieldParser.parse("a.b.c.field");

            assertEquals(0, level0.getNestingLevel());
            assertEquals(1, level1.getNestingLevel());
            assertEquals(2, level2.getNestingLevel());
            assertEquals(3, level3.getNestingLevel());

            assertFalse(level0.isCollection());
            assertTrue(level1.isCollection());
            assertTrue(level2.isCollection());
            assertTrue(level3.isCollection());
        }

        @Test
        @DisplayName("Multi-field at root should have empty prefix")
        void multiFieldAtRootShouldHaveEmptyPrefix() {
            ProjectionFieldParser.ProjectionField multi =
                    ProjectionFieldParser.parse("id,name,email");

            assertEquals("", multi.prefix(), "Root multi-field should have empty prefix");
            assertEquals(List.of("id", "name", "email"), multi.fields());
            assertFalse(multi.isSimpleField(), "Multi-field is not simple");
            assertFalse(multi.isCollection(), "Root multi-field is not a collection");
        }

        @Test
        @DisplayName("Should correctly identify simple fields")
        void shouldCorrectlyIdentifySimpleFields() {
            assertTrue(ProjectionFieldParser.parse("id").isSimpleField());
            assertTrue(ProjectionFieldParser.parse("name").isSimpleField());
            assertTrue(ProjectionFieldParser.parse("field_123").isSimpleField());

            assertFalse(ProjectionFieldParser.parse("user.id").isSimpleField());
            assertFalse(ProjectionFieldParser.parse("id,name").isSimpleField());
            assertFalse(ProjectionFieldParser.parse("name.name").isSimpleField());
        }

        @Test
        @DisplayName("getAllFullPaths should respect prefix distinction")
        void getAllFullPathsShouldRespectPrefixDistinction() {
            // Root level multi-field
            ProjectionFieldParser.ProjectionField root =
                    ProjectionFieldParser.parse("id,name,email");
            assertEquals(List.of("id", "name", "email"), root.getAllFullPaths());

            // Nested multi-field
            ProjectionFieldParser.ProjectionField nested =
                    ProjectionFieldParser.parse("user.id,name,email");
            assertEquals(
                    List.of("user.id", "user.name", "user.email"),
                    nested.getAllFullPaths()
            );
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("Should format simple field toString")
        void shouldFormatSimpleFieldToString() {
            assertEquals("name",
                    ProjectionFieldParser.parse("name").toString());
        }

        @Test
        @DisplayName("Should format nested field toString")
        void shouldFormatNestedFieldToString() {
            assertEquals("user.name",
                    ProjectionFieldParser.parse("user.name").toString());
            assertEquals("name.name",
                    ProjectionFieldParser.parse("name.name").toString());
        }

        @Test
        @DisplayName("Should format root multi-field toString")
        void shouldFormatRootMultiFieldToString() {
            assertEquals("[id,name,email]",
                    ProjectionFieldParser.parse("id,name,email").toString());
        }

        @Test
        @DisplayName("Should format nested multi-field toString")
        void shouldFormatNestedMultiFieldToString() {
            assertEquals("orders.items.[name,price]",
                    ProjectionFieldParser.parse("orders.items.name,price").toString());
        }
    }

    @Nested
    @DisplayName("Nesting Level Tests")
    class NestingLevelTests {

        @Test
        @DisplayName("Should calculate correct nesting levels")
        void shouldCalculateCorrectNestingLevels() {
            assertEquals(0, ProjectionFieldParser.parse("id").getNestingLevel());
            assertEquals(1, ProjectionFieldParser.parse("user.id").getNestingLevel());
            assertEquals(2, ProjectionFieldParser.parse("user.profile.id").getNestingLevel());
            assertEquals(3, ProjectionFieldParser.parse("a.b.c.id").getNestingLevel());
            assertEquals(5, ProjectionFieldParser.parse("a.b.c.d.e.f").getNestingLevel());
        }

        @Test
        @DisplayName("Nesting level should be consistent with prefix")
        void nestingLevelShouldBeConsistentWithPrefix() {
            ProjectionFieldParser.ProjectionField field =
                    ProjectionFieldParser.parse("user.profile.settings.theme");

            int expectedLevel = (int) field.prefix().chars()
                    .filter(ch -> ch == '.').count() + 1;
            assertEquals(expectedLevel, field.getNestingLevel());
        }
    }

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenariosTests {

        @Test
        @DisplayName("E-commerce order projection")
        void ecommerceOrderProjection() {
            Set<String> projectionSet = Set.of(
                    "orders[size=20,sort=date:desc].id,total,status",
                    "orders[size=20,sort=date:desc].customer.name,email",
                    "orders[size=20,sort=date:desc].items[size=10].product.name,price",
                    "orders[size=20,sort=date:desc].items[size=10].quantity"
            );

            Map<String, Pagination> options =
                    ProjectionFieldParser.parseCollectionOptions(projectionSet);

            // Verify orders pagination
            assertTrue(options.containsKey("orders"));
            assertEquals(20, options.get("orders").size());
            assertEquals("date", options.get("orders").sort().get(0).field());

            // Verify items pagination
            assertTrue(options.containsKey("orders.items"));
            assertEquals(10, options.get("orders.items").size());
        }

        @Test
        @DisplayName("Social media user profile projection")
        void socialMediaUserProfileProjection() {
            ProjectionFieldParser.ProjectionField userFields =
                    ProjectionFieldParser.parse("id,username,email,verified");
            ProjectionFieldParser.ProjectionField profileFields =
                    ProjectionFieldParser.parse("profile.bio,avatar,followers,following");
            ProjectionFieldParser.ProjectionField postFields =
                    ProjectionFieldParser.parse("posts[size=10,sort=createdAt:desc].title,content,likes");

            assertEquals("", userFields.prefix());
            assertEquals(4, userFields.fields().size());

            assertEquals("profile", profileFields.prefix());
            assertEquals(4, profileFields.fields().size());

            assertEquals("posts", postFields.prefix());
            assertEquals(3, postFields.fields().size());
        }

        @Test
        @DisplayName("Complex hierarchical data structure")
        void complexHierarchicalDataStructure() {
            String spec = "company.departments[size=5].employees[size=20,sort=lastName:asc].firstName,lastName,position";

            ProjectionFieldParser.ProjectionField field = ProjectionFieldParser.parse(spec);
            assertEquals("company.departments.employees", field.prefix());
            assertEquals(List.of("firstName", "lastName", "position"), field.fields());

            Map<String, Pagination> options =
                    ProjectionFieldParser.parseCollectionOptions(Set.of(spec));

            assertTrue(options.containsKey("company.departments"));
            assertTrue(options.containsKey("company.departments.employees"));
            assertEquals(5, options.get("company.departments").size());
            assertEquals(20, options.get("company.departments.employees").size());
        }

        @Test
        @DisplayName("API response with mixed root and nested fields")
        void apiResponseWithMixedFields() {
            Set<String> projectionSet = Set.of(
                    "id",
                    "createdAt",
                    "updatedAt",
                    "user.id,username",
                    "user.profile.avatar,bio",
                    "metadata.tags,category"
            );

            // Parse each field
            ProjectionFieldParser.ProjectionField id = ProjectionFieldParser.parse("id");
            ProjectionFieldParser.ProjectionField createdAt = ProjectionFieldParser.parse("createdAt");
            ProjectionFieldParser.ProjectionField user = ProjectionFieldParser.parse("user.id,username");
            ProjectionFieldParser.ProjectionField profile = ProjectionFieldParser.parse("user.profile.avatar,bio");

            // Verify root fields
            assertTrue(id.isSimpleField());
            assertTrue(createdAt.isSimpleField());
            assertEquals("", id.prefix());
            assertEquals("", createdAt.prefix());

            // Verify nested fields
            assertTrue(user.isCollection());
            assertTrue(profile.isCollection());
            assertEquals("user", user.prefix());
            assertEquals("user.profile", profile.prefix());
            assertEquals(1, user.getNestingLevel());
            assertEquals(2, profile.getNestingLevel());
        }
    }

    @Nested
    @DisplayName("Performance and Edge Cases")
    class PerformanceAndEdgeCasesTests {

        @Test
        @DisplayName("Should handle very deep nesting")
        void shouldHandleVeryDeepNesting() {
            String deepPath = "a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y.z";
            ProjectionFieldParser.ProjectionField field = ProjectionFieldParser.parse(deepPath);

            assertEquals("a.b.c.d.e.f.g.h.i.j.k.l.m.n.o.p.q.r.s.t.u.v.w.x.y", field.prefix());
            assertEquals(List.of("z"), field.fields());
            assertEquals(25, field.getNestingLevel());
        }

        @Test
        @DisplayName("Should handle many fields in multi-field projection")
        void shouldHandleManyFieldsInMultiField() {
            List<String> manyFields = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                manyFields.add("field" + i);
            }
            String spec = "prefix." + String.join(",", manyFields);

            ProjectionFieldParser.ProjectionField field = ProjectionFieldParser.parse(spec);
            assertEquals("prefix", field.prefix());
            assertEquals(100, field.fields().size());
            assertEquals(100, field.getAllFullPaths().size());
        }

        @Test
        @DisplayName("Should handle mixed whitespace gracefully")
        void shouldHandleMixedWhitespaceGracefully() {
            ProjectionFieldParser.ProjectionField field =
                    ProjectionFieldParser.parse("  user.profile.name , email , phone  ");

            assertEquals("user.profile", field.prefix());
            assertEquals(List.of("name", "email", "phone"), field.fields());
        }

        @Test
        @DisplayName("Should parse large projection set efficiently")
        void shouldParseLargeProjectionSetEfficiently() {
            Set<String> largeSet = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                largeSet.add("collection" + i + "[size=10].field");
            }

            long startTime = System.currentTimeMillis();
            Map<String, Pagination> options =
                    ProjectionFieldParser.parseCollectionOptions(largeSet);
            long endTime = System.currentTimeMillis();

            assertEquals(1000, options.size());
            assertTrue(endTime - startTime < 1000,
                    "Parsing should complete in under 1 second");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("ProjectionFields with same values should be equal")
        void projectionFieldsWithSameValuesShouldBeEqual() {
            ProjectionFieldParser.ProjectionField field1 =
                    new ProjectionFieldParser.ProjectionField("user", List.of("id", "name"));
            ProjectionFieldParser.ProjectionField field2 =
                    new ProjectionFieldParser.ProjectionField("user", List.of("id", "name"));

            assertEquals(field1, field2);
            assertEquals(field1.hashCode(), field2.hashCode());
        }

        @Test
        @DisplayName("ProjectionFields with different prefixes should not be equal")
        void projectionFieldsWithDifferentPrefixesShouldNotBeEqual() {
            ProjectionFieldParser.ProjectionField field1 =
                    new ProjectionFieldParser.ProjectionField("", List.of("name"));
            ProjectionFieldParser.ProjectionField field2 =
                    new ProjectionFieldParser.ProjectionField("name", List.of("name"));

            assertNotEquals(field1, field2);
            assertNotEquals(field1.getFullPath(), field2.getFullPath());
        }

        @Test
        @DisplayName("ProjectionFields with different fields should not be equal")
        void projectionFieldsWithDifferentFieldsShouldNotBeEqual() {
            ProjectionFieldParser.ProjectionField field1 =
                    new ProjectionFieldParser.ProjectionField("user", List.of("id"));
            ProjectionFieldParser.ProjectionField field2 =
                    new ProjectionFieldParser.ProjectionField("user", List.of("name"));

            assertNotEquals(field1, field2);
        }
    }

    @Nested
    @DisplayName("Documentation Example Tests")
    class DocumentationExampleTests {

        @Test
        @DisplayName("All examples from JavaDoc should work")
        void allExamplesFromJavaDocShouldWork() {
            // Simple field
            ProjectionFieldParser.ProjectionField simple = ProjectionFieldParser.parse("name");
            assertEquals("", simple.prefix());
            assertEquals(List.of("name"), simple.fields());

            // Nested field
            ProjectionFieldParser.ProjectionField nested =
                    ProjectionFieldParser.parse("address.city.name");
            assertEquals("address.city", nested.prefix());
            assertEquals(List.of("name"), nested.fields());

            // Multi-field collection
            ProjectionFieldParser.ProjectionField multi =
                    ProjectionFieldParser.parse("orders.items.productName,price");
            assertEquals("orders.items", multi.prefix());
            assertEquals(List.of("productName", "price"), multi.fields());

            // Hierarchical pagination
            Map<String, Pagination> options = ProjectionFieldParser.parseCollectionOptions(
                    Set.of("orders[size=5].items[page=0,sort=id:asc].name")
            );
            assertTrue(options.containsKey("orders"));
            assertTrue(options.containsKey("orders.items"));
        }
    }
}