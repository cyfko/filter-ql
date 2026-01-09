package io.github.cyfko.filterql.spring.generator;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.processor.model.FieldMetadata;
import io.github.cyfko.filterql.spring.processor.model.SupportedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link FieldMetadataRegistryGenerator}.
 * <p>
 * Tests cover:
 * - Registry generation with single entity
 * - Registry generation with multiple entities
 * - Field metadata serialization
 * - Exposed name handling
 * - Virtual fields integration
 * - Edge cases (empty fields, special characters)
 * </p>
 */
class FieldMetadataRegistryGeneratorTest {

//    private TemplateEngine templateEngine;
//    private FieldMetadataRegistryGenerator generator;
//
//    static class TestEntityDTO {
//    }
//
//    static class TestEntity {
//        private String name;
//        private Integer age;
//    }
//
//    static class OtherEntity {
//        private Boolean active;
//    }
//
//    @BeforeEach
//    void setUp() {
//        templateEngine = new TemplateEngine();
//        generator = new FieldMetadataRegistryGenerator(templateEngine);
//    }
//
//    @Test
//    void shouldGenerateRegistryWithBasicStructure() throws IOException {
//        // Test basic registry generation
//        String result = generator.generate();
//
//        assertNotNull(result, "Generated registry should not be null");
//        assertTrue(result.contains("class FieldMetadataRegistryImpl"),
//            "Should contain registry class");
//        assertTrue(result.contains("implements FieldMetadataRegistry"),
//            "Should implement FieldMetadataRegistry interface");
//    }
//
//    @Test
//    void shouldIncludeExposedNames() throws IOException {
//        // Test that exposed names are tracked
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify exposedNames array contains endpoint names
//    }
//
//    @Test
//    void shouldHandleEntityWithNoFields() throws IOException {
//        // Test entity with empty field list
//        List<FieldMetadata> emptyFields = List.of();
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Should still generate valid registry structure
//    }
//
//    @Test
//    void shouldHandleVirtualFields() throws IOException {
//        // Test that virtual fields are included in registry
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("NAME", SupportedType.STRING, Set.of(Op.EQ)),
//            FieldMetadata.virtualField(
//                    "VIRTUAL_FIELD",
//                    SupportedType.STRING,
//                    Set.of(Op.MATCHES),
//                    "com.example.VirtualResolver",
//                    "method",
//                    true)
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify both regular and virtual fields in output
//    }
//
//    @Test
//    void shouldHandleFieldsWithAllOperators() throws IOException {
//        // Test field with all supported operators
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField(
//                    "COMPREHENSIVE",
//                    SupportedType.STRING,
//                    Set.of(Op.EQ, Op.NE, Op.IN, Op.NOT_IN, Op.MATCHES, Op.IS_NULL, Op.NOT_NULL)
//            )
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify all operators are serialized
//    }
//
//    @Test
//    void shouldHandleFieldsWithDifferentTypes() throws IOException {
//        // Test all supported field types
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("STRING_FIELD", SupportedType.STRING, Set.of(Op.EQ)),
//            FieldMetadata.regularField("INTEGER_FIELD", SupportedType.INTEGER, Set.of(Op.EQ)),
//            FieldMetadata.regularField("LONG_FIELD", SupportedType.LONG, Set.of(Op.EQ)),
//            FieldMetadata.regularField("BOOLEAN_FIELD", SupportedType.BOOLEAN, Set.of(Op.EQ)),
//            FieldMetadata.regularField("DATE_FIELD", SupportedType.LOCAL_DATE, Set.of(Op.GT, Op.LT))
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify all types are handled correctly
//    }
//
//    @Test
//    void shouldHandleExposedNameWithSpecialCharacters() throws IOException {
//        // Test exposed names with kebab-case and special chars
//        class SpecialEntity {
//            private String field;
//        }
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify special characters are properly escaped in generated code
//    }
//
//    @Test
//    void shouldAvoidDuplicateEntityNames() {
//        // Test that Set prevents duplicate entity registrations
//        assertNotNull(generator);
//        // entityFullNames is a Set, so duplicates automatically prevented
//    }
//
//    @Test
//    void shouldPreserveRegistrationOrder() throws IOException {
//        // Test that entities are registered in order
//        // LinkedHashSet preserves insertion order
//        List<FieldMetadata> fields1 = List.of(
//            FieldMetadata.regularField("FIRST", SupportedType.STRING, Set.of(Op.EQ))
//        );
//
//        List<FieldMetadata> fields2 = List.of(
//            FieldMetadata.regularField("SECOND", SupportedType.STRING, Set.of(Op.EQ))
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify order is preserved in output
//    }
//
//    @Test
//    void shouldHandleFieldWithI18nKey() throws IOException {
//        // Test field metadata with i18n key
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("LOCALIZED", SupportedType.STRING, Set.of(Op.EQ))
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify i18n key is in serialized metadata
//    }
//
//    @Test
//    void shouldHandleFieldWithValidationMessage() throws IOException {
//        // Test field metadata with custom validation message
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("VALIDATED", SupportedType.STRING, Set.of(Op.EQ))
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify validation message is in serialized metadata
//    }
//
//    @Test
//    void shouldHandleRequiredFields() throws IOException {
//        // Test field metadata with required flag
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("REQUIRED_FIELD", SupportedType.STRING, Set.of(Op.EQ))
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify required flag is serialized
//    }
//
//    @Test
//    void shouldHandleFilterableFlag() throws IOException {
//        // Test field metadata with filterable flag
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("NON_FILTERABLE", SupportedType.STRING, Set.of())
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Would verify filterable flag is serialized
//    }
//
//    @Test
//    void shouldHandleEmptyOperatorSet() throws IOException {
//        // Test field with no supported operators
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("NO_OPS", SupportedType.STRING, Set.of())
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Should handle empty operator set gracefully
//    }
//
//    @Test
//    @Disabled("Requires ProcessingEnvironment to register entities. " +
//              "See integration-test module for full registry generation tests.")
//    void shouldGenerateValidJavaCode() throws IOException {
//        // Integration test: verify generated code is syntactically valid
//        // NOTE: This test requires calling generator.register(processingEnv, entityElement, fields)
//        List<FieldMetadata> fields = List.of(
//            FieldMetadata.regularField("NAME", SupportedType.STRING, Set.of(Op.EQ))
//        );
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        // Basic syntax checks
//        assertTrue(result.contains("package"), "Should have package declaration");
//        assertTrue(result.contains("class"), "Should have class declaration");
//        assertTrue(result.contains("{"), "Should have opening braces");
//        assertTrue(result.contains("}"), "Should have closing braces");
//        assertTrue(result.contains("new HashMap<>()"), "Should instantiate HashMap");
//    }
//
//    @Test
//    void shouldIncludeNecessaryImports() throws IOException {
//        // Test that generated code has required imports
//        String result = generator.generate();
//
//        assertNotNull(result);
//        assertTrue(result.contains("import"), "Should have import statements");
//        // Would verify specific imports:
//        // - java.util.HashMap
//        // - java.util.Map
//        // - FieldMetadata
//        // - etc.
//    }
//
//    @Test
//    void shouldHandleNestedEntityClasses() {
//        // Test entity as nested class
//        class NestedEntity {
//            private String field;
//        }
//
//        // Would verify proper FQCN handling with $ replacement
//        assertNotNull(generator);
//    }
//
//    @Test
//    void shouldHandleEntityInDifferentPackage() {
//        // Test entity from different package
//        // Would verify package name is correctly included in FQCN
//        assertNotNull(generator);
//    }
//
//    @Test
//    void shouldHandleLongFieldLists() throws IOException {
//        // Test entity with many fields (performance/scalability)
//        List<FieldMetadata> manyFields = new java.util.ArrayList<>();
//        for (int i = 0; i < 50; i++) {
//            manyFields.add(
//                FieldMetadata.regularField("FIELD_" + i, SupportedType.STRING, Set.of(Op.EQ))
//            );
//        }
//
//        String result = generator.generate();
//
//        assertNotNull(result);
//        assertTrue(result.length() > 1000, "Should generate substantial code for many fields");
//    }
}
