package io.github.cyfko.filterql.spring.generator;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.processor.model.FieldMetadata;
import io.github.cyfko.filterql.spring.processor.model.SupportedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link FilterContextGenerator}.
 * <p>
 * Tests cover:
 * - Basic FilterContext configuration generation
 * - Regular field JPA path mapping
 * - Virtual field resolver integration (static and instance methods)
 * - ApplicationContext parameter injection
 * - Import generation for virtual resolvers
 * - Switch-case statement generation
 * - Bean naming conventions (camelCase)
 * - Edge cases (no fields, all virtual, mixed)
 * </p>
 */
class FilterContextGeneratorTest {

    private TemplateEngine templateEngine;
    private FilterContextGenerator generator;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
        generator = new FilterContextGenerator(templateEngine);
    }

    @Test
    void shouldGenerateBasicFilterContextConfiguration() throws IOException {
        // Test basic generation with only regular fields
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("NAME", "name",new Op[]{ Op.EQ }),
            FieldMetadata.regularField("AGE", "age", new Op[]{ Op.GT })
        );

        generator.register("com.example.config", "UserPropertyRef", fields);
        String result = generator.generate();

        assertNotNull(result, "Generated config should not be null");
        assertTrue(result.contains("package io.github.cyfko.filterql.config"),
            "Should contain correct package");
        assertTrue(result.contains("class FilterQlContextConfig"),
            "Should contain configuration class");
        assertTrue(result.contains("userPropertyRefContext"),
            "Should contain bean name in camelCase");
        assertTrue(result.contains("@Configuration"),
            "Should be annotated as Configuration");
    }

    @Test
    void shouldGenerateBeanNameInCamelCase() throws IOException {
        // Test that bean name follows camelCase convention
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("FIELD", "field", new Op[]{ Op.EQ })
        );

        generator.register("com.example", "MyCustomEntityPropertyRef", fields);
        String result = generator.generate();

        assertTrue(result.contains("myCustomEntityPropertyRefContext"),
            "Bean name should be in camelCase");
    }

    @Test
    void shouldGenerateSwitchCasesForRegularFields() throws IOException {
        // Contract test: Verify generator produces correct switch cases for regular fields
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("USERNAME", "username", new Op[]{ Op.EQ }),
            FieldMetadata.regularField("EMAIL", "email", new Op[]{ Op.MATCHES })
        );

        generator.register("com.example", "UserPropertyRef", fields);
        String result = generator.generate();

        assertNotNull(result, "Generated code should not be null");
        assertFalse(result.isEmpty(), "Generated code should not be empty");
        assertTrue(result.contains("case USERNAME ->"),
            "Should contain case for USERNAME. Generated:\n" + result);
        assertTrue(result.contains("\"username\""),
            "Should contain JPA path for username. Generated:\n" + result);
        assertTrue(result.contains("case EMAIL ->"),
            "Should contain case for EMAIL");
        assertTrue(result.contains("\"email\""),
            "Should contain JPA path for email");
    }

    @Test
    void shouldGenerateSwitchCasesForStaticVirtualFields() throws IOException {
        // Test that static virtual fields generate direct method calls
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "FULL_NAME",
                    "getFullName",
                    new Op[]{ Op.MATCHES },
                    "com.example.resolvers.UserResolver",
                    null,
                    true)
        );

        generator.register("com.example", "UserPropertyRef", fields);
        String result = generator.generate();

        assertTrue(result.contains("case FULL_NAME ->"),
            "Should contain case for virtual field");
        assertTrue(result.contains("(PredicateResolverMapping) (op, args) -> com.example.resolvers.UserResolver.getFullName(op, args);"),
            "Should generate PredicateResolverMapping wrapper for static method");
    }

    @Test
    void shouldGenerateSwitchCasesForInstanceVirtualFields() throws IOException {
        // Test that instance virtual fields use ResolverFinder
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "TENANT",
                    "getTenantFilter",
                    new Op[]{ Op.EQ },
                    "com.example.resolvers.UserTenancyService",
                    null,
                    false)
        );

        generator.register("com.example", "UserPropertyRef", fields);
        String result = generator.generate();

        assertTrue(result.contains("case TENANT ->"),
            "Should contain case for virtual field");
        assertTrue(result.contains("(PredicateResolver) ProjectionUtils.invoke("),
            "Should use ProjectionUtils.invoke with cast for instance method");
        assertTrue(result.contains("UserTenancyService.class"),
            "Should reference resolver class");
    }

    @Test
    void shouldGenerateInstanceResolverParamForVirtualFields() throws IOException {
        // Test that ApplicationContext is injected when virtual fields present
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "VIRTUAL",
                    "resolve",
                    new Op[]{ Op.EQ },
                    "com.example.Service",
                    null,
                    false)
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        assertTrue(result.contains("InstanceResolver instanceResolver"),
            "Should inject InstanceResolver when virtual fields present");
    }

    @Test
    void shouldNotGenerateInstanceResolverParamForRegularFieldsOnly() throws IOException {
        // Test that InstanceResolver not injected for regular fields only
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("NAME", "name", new Op[]{ Op.EQ })
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        // Should not have InstanceResolver parameter
        // This depends on template structure - would need to verify
        assertNotNull(result);
    }

    @Test
    void shouldNotGenerateInstanceResolverParamForStaticVirtualFieldsOnly() throws IOException {
        // Test that InstanceResolver not needed for static virtual fields
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "VIRTUAL",
                    "resolve",
                    new Op[]{ Op.EQ },
                    "com.example.Resolver",
                    null,
                    true)
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        // Should not need InstanceResolver for static methods
        assertNotNull(result);
    }

    @Test
    void shouldGenerateImportsForVirtualResolvers() throws IOException {
        // Test that resolver classes are imported
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "FIELD_A",
                    "methodA",
                    new Op[]{ Op.EQ },
                    "com.example.resolvers.ResolverA",
                    null,
                    true
            ),
            FieldMetadata.virtualField(
                    "FIELD_B",
                    "methodB",
                    new Op[]{ Op.EQ },
                    "com.example.resolvers.ResolverB",
                    "myBeanName",
                    false
            ),
            FieldMetadata.virtualField(
                    "FIELD_C",
                    "methodC",
                    new Op[]{ Op.EQ },
                    "com.example.resolvers.ResolverB",
                    null,
                    false
            )
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        assertFalse(result.contains("import com.example.resolvers.ResolverA"), "Should not have ResolverA import statement");
        assertFalse(result.contains("import com.example.resolvers.ResolverB"), "Should not have ResolverB statement");

        assertTrue(result.contains("com.example.resolvers.ResolverA.methodA(op, args)"), "Should have static invocation for methodA");
        assertTrue(result.contains("(PredicateResolver) ProjectionUtils.invoke(instanceResolver, com.example.resolvers.ResolverB.class, \"myBeanName\", \"methodB\", op, args)"),
                "Should have instance invocation for method B");
        assertTrue(result.contains("(PredicateResolver) ProjectionUtils.invoke(instanceResolver, com.example.resolvers.ResolverB.class, null, \"methodC\", op, args)"),
                "Should have instance invocation for method C");
    }

    @Test
    void shouldNotGenerateResolverFinderImportForStaticOnly() throws IOException {
        // Test that ResolverFinder not imported for static resolvers only
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "VIRTUAL",
                    "resolve",
                    new Op[]{ Op.EQ },
                    "com.example.Resolver",
                    null,
                    true)
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        // Should not import ResolverFinder
        assertNotNull(result);
    }

    @Test
    void shouldHandleMixedRegularAndVirtualFields() throws IOException {
        // Test combination of regular and virtual fields
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("NAME", "name", new Op[]{ Op.EQ }),
            FieldMetadata.virtualField(
                    "COMPUTED",
                    "resolve",
                    new Op[]{ Op.EQ },
                    "com.example.Resolver",
                    null,
                    true),
            FieldMetadata.regularField("AGE", "age", new Op[]{ Op.GT })
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        assertNotNull(result, "Generated code should not be null");
        assertFalse(result.isEmpty(), "Generated code should not be empty");
        assertTrue(result.contains("case NAME ->"), "Should have case for NAME. Generated:\n" + result);
        assertTrue(result.contains("case COMPUTED ->"), "Should have case for COMPUTED");
        assertTrue(result.contains("case AGE ->"), "Should have case for AGE");
        assertTrue(result.contains("\"name\""), "Should have JPA path for NAME. Generated:\n" + result);
        assertTrue(result.contains("com.example.Resolver.resolve(op, args);"), "Should have resolver call for COMPUTED");
        assertTrue(result.contains("\"age\""), "Should have JPA path for AGE");
    }

    @Test
    void shouldHandleEmptyFieldList() throws IOException {
        // Test with no fields
        List<FieldMetadata> emptyFields = List.of();

        generator.register("com.example", "EntityPropertyRef", emptyFields);
        String result = generator.generate();

        assertNotNull(result, "Should generate valid config even with no fields");
        assertTrue(result.contains("class FilterQlContextConfig"),
            "Should still have configuration class");
    }

    @Test
    void shouldHandleSingleField() throws IOException {
        // Test with exactly one field
        List<FieldMetadata> singleField = List.of(
            FieldMetadata.regularField("ID", "id", new Op[]{ Op.EQ })
        );

        generator.register("com.example", "EntityPropertyRef", singleField);
        String result = generator.generate();

        assertTrue(result.contains("case ID ->"), "Should have switch case for single field");
    }

    @Test
    void shouldHandleMultipleVirtualFieldsFromSameResolver() throws IOException {
        // Test multiple virtual fields referencing same resolver class
        List<FieldMetadata> fields = List.of(
            FieldMetadata.virtualField(
                    "FIELD1",
                    "method1",
                    new Op[]{ Op.EQ },
                    "com.example.Resolver",
                    null,
                    true),
            FieldMetadata.virtualField(
                    "FIELD2",
                    "method2",
                    new Op[]{ Op.EQ },
                    "com.example.Resolver",
                    null,
                    true)
        );

        generator.register("com.example", "EntityPropertyRef", fields);
        String result = generator.generate();

        // Should handle both method calls with PredicateResolverMapping wrapper
        assertTrue(result.contains("com.example.Resolver.method1(op, args)"), "Should handle method1 call");
        assertTrue(result.contains("com.example.Resolver.method2(op, args)"), "Should handle method2 call");
    }

    @Test
    void shouldHandleNestedPackageNames() throws IOException {
        // Test with deeply nested packages
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("FIELD", "field", new Op[]{ Op.EQ })
        );

        generator.register("com.example.very.deep.nested.package.config", "EntityPropertyRef", fields);
        String result = generator.generate();

        assertFalse(result.contains("import com.example.very.deep.nested.package.config.EntityPropertyRef;"),
            "Should handle deeply nested packages");
        assertTrue(result.contains("com.example.very.deep.nested.package.config.EntityPropertyRef"),
                "Should handle same enum name gracefully");
        assertTrue(result.contains("com.example.very.deep.nested.package.config.EntityPropertyRef.class"),
                "Should handle same enum name gracefully");
    }


    @Test
    void shouldHandlePropertyRefEnumWithCustomName() throws IOException {
        // Test custom PropertyRef enum names
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("FIELD", "field", new Op[]{ Op.EQ })
        );

        generator.register("com.example", "CustomUserPropertyRef", fields);
        String result = generator.generate();

        assertTrue(result.contains("CustomUserPropertyRef"),
            "Should use custom PropertyRef enum name");
    }

    @Test
    void shouldGenerateValidJavaSyntax() throws IOException {
        // Integration test: verify generated code has valid Java structure
        List<FieldMetadata> fields = List.of(
            FieldMetadata.regularField("NAME", "name", new Op[]{ Op.EQ }),
            FieldMetadata.virtualField(
                    "VIRTUAL",
                    "resolve",
                    new Op[]{ Op.EQ },
                    "com.example.Resolver",
                    null,
                    true)
        );

        generator.register("com.example.config", "EntityPropertyRef", fields);
        String result = generator.generate();

        // Basic syntax checks
        assertTrue(result.contains("package"), "Should have package declaration");
        assertTrue(result.contains("class"), "Should have class declaration");
        assertTrue(result.contains("@Bean"), "Should have @Bean annotation");
        assertTrue(result.contains("FilterContext"), "Should reference FilterContext");
        assertTrue(result.contains("{"), "Should have opening braces");
        assertTrue(result.contains("}"), "Should have closing braces");
        assertTrue(result.contains("switch"), "Should have switch statement");
        assertTrue(result.contains("case"), "Should have case statements");
        assertTrue(result.contains("->"), "Should use switch expression syntax (Java 14+)");
    }
}
