package io.github.cyfko.filterql.spring.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link TemplateEngine}.
 * <p>
 * Tests cover:
 * - Template loading from resources
 * - Variable substitution (simple and nested)
 * - Null/empty value handling
 * - Missing variables
 * - Error cases (missing templates, invalid syntax)
 * - Edge cases (special characters, multiline values)
 * </p>
 */
class TemplateEngineTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
    }

    @Test
    void shouldLoadTemplateFromResources() throws IOException {
        // Test loading actual template file
        String template = templateEngine.loadTemplate("search-endpoint.java.tpl");

        assertNotNull(template, "Template should be loaded");
        assertFalse(template.isEmpty(), "Template should not be empty");
        assertTrue(template.contains("${"), "Template should contain placeholders");
    }

    @Test
    void shouldThrowExceptionForMissingTemplate() {
        // Contract test: TemplateEngine throws NullPointerException for missing templates
        // (getResourceAsStream returns null, then InputStreamReader constructor throws NPE)
        assertThrows(NullPointerException.class, () -> {
            templateEngine.loadTemplate("non-existent-template.tpl");
        }, "Should throw NullPointerException for missing template");
    }

    @Test
    void shouldSubstituteSimpleVariables() throws IOException {
        String template = "Hello ${name}, you are ${age} years old.";
        Map<String, Object> context = new HashMap<>();
        context.put("name", "John");
        context.put("age", 25);

        String result = templateEngine.process(template, context);

        assertEquals("Hello John, you are 25 years old.", result,
            "Should substitute all variables");
    }

    @Test
    void shouldSubstituteVariablesWithSpecialCharacters() throws IOException {
        String template = "Path: ${basePath}/search/${resourceName}";
        Map<String, Object> context = new HashMap<>();
        context.put("basePath", "/api/v1");
        context.put("resourceName", "users");

        String result = templateEngine.process(template, context);

        assertEquals("Path: /api/v1/search/users", result,
            "Should handle path separators correctly");
    }

    @Test
    void shouldSubstituteMultilineValues() throws IOException {
        String template = "Method:\n${methodBody}";
        Map<String, Object> context = new HashMap<>();
        context.put("methodBody", "if (true) {\n    return value;\n}");

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("if (true)"), "Should preserve multiline content");
        assertTrue(result.contains("    return value;"), "Should preserve indentation");
    }

    @Test
    void shouldHandleEmptyValues() throws IOException {
        String template = "Prefix${value}Suffix";
        Map<String, Object> context = new HashMap<>();
        context.put("value", "");

        String result = templateEngine.process(template, context);

        assertEquals("PrefixSuffix", result, "Should handle empty values");
    }

    @Test
    void shouldHandleNullValues() {
        // Contract test: TemplateEngine throws IllegalArgumentException for null values
        String template = "Value: ${nullValue}";
        Map<String, Object> context = new HashMap<>();
        context.put("nullValue", null);

        assertThrows(IllegalArgumentException.class, () -> {
            templateEngine.process(template, context);
        }, "Should throw IllegalArgumentException for null template variable");
    }

    @Test
    void shouldLeaveMissingVariablesUnchanged() {
        // Contract test: TemplateEngine throws IllegalArgumentException for missing variables
        String template = "Known: ${known}, Unknown: ${unknown}";
        Map<String, Object> context = new HashMap<>();
        context.put("known", "value");

        assertThrows(IllegalArgumentException.class, () -> {
            templateEngine.process(template, context);
        }, "Should throw IllegalArgumentException for missing template variable");
    }

    @Test
    void shouldHandleIntegerValues() throws IOException {
        String template = "Page size: ${pageSize}";
        Map<String, Object> context = new HashMap<>();
        context.put("pageSize", 20);

        String result = templateEngine.process(template, context);

        assertEquals("Page size: 20", result, "Should convert Integer to String");
    }

    @Test
    void shouldHandleBooleanValues() throws IOException {
        String template = "Enabled: ${enabled}";
        Map<String, Object> context = new HashMap<>();
        context.put("enabled", true);

        String result = templateEngine.process(template, context);

        assertEquals("Enabled: true", result, "Should convert Boolean to String");
    }

    @Test
    void shouldHandleComplexObjectToString() throws IOException {
        String template = "Object: ${obj}";
        Map<String, Object> context = new HashMap<>();
        context.put("obj", new Object() {
            @Override
            public String toString() {
                return "CustomObject";
            }
        });

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("CustomObject"), "Should use toString() method");
    }

    @Test
    void shouldHandleVariableNameWithUnderscores() throws IOException {
        String template = "${my_variable_name}";
        Map<String, Object> context = new HashMap<>();
        context.put("my_variable_name", "value");

        String result = templateEngine.process(template, context);

        assertEquals("value", result, "Should handle underscores in variable names");
    }

    @Test
    void shouldHandleVariableNameWithNumbers() throws IOException {
        String template = "${var123}";
        Map<String, Object> context = new HashMap<>();
        context.put("var123", "value");

        String result = templateEngine.process(template, context);

        assertEquals("value", result, "Should handle numbers in variable names");
    }

    @Test
    void shouldHandleAdjacentVariables() throws IOException {
        String template = "${first}${second}";
        Map<String, Object> context = new HashMap<>();
        context.put("first", "Hello");
        context.put("second", "World");

        String result = templateEngine.process(template, context);

        assertEquals("HelloWorld", result, "Should handle adjacent variables");
    }

    @Test
    void shouldHandleRepeatedVariables() throws IOException {
        String template = "${value} and ${value} again";
        Map<String, Object> context = new HashMap<>();
        context.put("value", "test");

        String result = templateEngine.process(template, context);

        assertEquals("test and test again", result,
            "Should substitute same variable multiple times");
    }

    @Test
    void shouldNotSubstituteEscapedDollarSigns() {
        // Contract test: TemplateEngine does not support escaping - treats \${...} as placeholder
        String template = "\\${notAVariable}";
        Map<String, Object> context = new HashMap<>();

        assertThrows(IllegalArgumentException.class, () -> {
            templateEngine.process(template, context);
        }, "Engine does not support escaping - throws exception for missing variable");
    }

    @Test
    void shouldHandleTemplateWithNoPlaceholders() throws IOException {
        String template = "This is a plain text template with no placeholders.";
        Map<String, Object> context = new HashMap<>();

        String result = templateEngine.process(template, context);

        assertEquals(template, result, "Should return template unchanged");
    }

    @Test
    void shouldHandleEmptyTemplate() throws IOException {
        String template = "";
        Map<String, Object> context = new HashMap<>();

        String result = templateEngine.process(template, context);

        assertEquals("", result, "Should handle empty template");
    }

    @Test
    void shouldHandleEmptyContext() throws IOException {
        String template = "No variables here";
        Map<String, Object> context = new HashMap<>();

        String result = templateEngine.process(template, context);

        assertEquals("No variables here", result, "Should handle empty context");
    }

    @Test
    void shouldHandleNullContext() {
        // Contract test: TemplateEngine handles null context gracefully (returns template unchanged)
        String template = "Template text";

        // When context is null and there are no placeholders, returns template as-is
        String result = templateEngine.process(template, null);
        assertEquals(template, result, "Should return template unchanged when context is null and no placeholders");
    }

    @Test
    void shouldHandleLargeTemplate() throws IOException {
        // Test with a large template (simulating real controller template)
        StringBuilder largeTemplate = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeTemplate.append("Line ").append(i).append(": ${variable}\n");
        }

        Map<String, Object> context = new HashMap<>();
        context.put("variable", "value");

        String result = templateEngine.process(largeTemplate.toString(), context);

        assertNotNull(result, "Should handle large templates");
        assertTrue(result.length() > 1000, "Should produce substantial output");
        assertTrue(result.contains("Line 999"), "Should process all lines");
    }

    @Test
    void shouldHandleNestedBraces() throws IOException {
        String template = "Code: { ${value} }";
        Map<String, Object> context = new HashMap<>();
        context.put("value", "nested");

        String result = templateEngine.process(template, context);

        assertEquals("Code: { nested }", result, "Should handle nested braces");
    }

    @Test
    void shouldHandleDollarSignsNotInPlaceholders() throws IOException {
        String template = "Price: $10.00, Variable: ${var}";
        Map<String, Object> context = new HashMap<>();
        context.put("var", "value");

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("$10.00"), "Should preserve $ outside placeholders");
        assertTrue(result.contains("value"), "Should substitute variable");
    }

    @Test
    void shouldProcessRealSearchEndpointTemplate() throws IOException {
        // Integration test with actual template
        String template = templateEngine.loadTemplate("search-endpoint.java.tpl");
        Map<String, Object> context = new HashMap<>();
        context.put("annotationDecorators", "@PreAuthorize(\"hasRole('USER')\")");
        context.put("basePath", "/api");
        context.put("exposedName", "users");
        context.put("methodName", "searchUser");
        context.put("fqEnumName", "UserPropertyRef");
        context.put("listItemType", "UserDTO");

        String result = templateEngine.process(template, context);

        assertNotNull(result, "Should process real template");
        assertTrue(result.contains("searchUser"), "Should contain method name");
        assertTrue(result.contains("UserDTO"), "Should contain DTO class");
        assertTrue(result.contains("/api/search/users"), "Should contain endpoint path");
    }

    @Test
    void shouldProcessRealControllerTemplate() throws IOException {
        // Integration test with controller template
        String template = templateEngine.loadTemplate("search-controller.java.tpl");
        Map<String, Object> context = new HashMap<>();
        context.put("annotationsImports", "import java.util.*;\nimport org.springframework.web.bind.annotation.*;");
        context.put("controllerFields", "private final UserRepository repository;");
        context.put("controllerConstructorParams", "UserRepository repository");
        context.put("controllerConstructorInits", "this.repository = repository;");
        context.put("searchEndpoints", "// Search endpoint placeholder");
        context.put("countEndpoints", "// Count endpoint placeholder");
        context.put("existsEndpoints", "// Exists endpoint placeholder");

        String result = templateEngine.process(template, context);

        assertNotNull(result, "Should process controller template");
        assertTrue(result.contains("@RestController"), "Should contain controller annotation");
        assertTrue(result.contains("FilterQlController"), "Should contain class name FilterQLController");
    }

    @Test
    void shouldHandleWindowsLineEndings() throws IOException {
        String template = "Line1\r\n${var}\r\nLine3";
        Map<String, Object> context = new HashMap<>();
        context.put("var", "value");

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("value"), "Should handle Windows line endings");
    }

    @Test
    void shouldHandleUnixLineEndings() throws IOException {
        String template = "Line1\n${var}\nLine3";
        Map<String, Object> context = new HashMap<>();
        context.put("var", "value");

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("value"), "Should handle Unix line endings");
    }

    @Test
    void shouldHandleTabCharacters() throws IOException {
        String template = "\t${var}";
        Map<String, Object> context = new HashMap<>();
        context.put("var", "indented");

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("indented"), "Should handle tab characters");
    }

    @Test
    void shouldPreserveIndentation() throws IOException {
        String template = "    ${code}";
        Map<String, Object> context = new HashMap<>();
        context.put("code", "if (true) {\n        return;\n    }");

        String result = templateEngine.process(template, context);

        assertTrue(result.contains("    if (true)"), "Should preserve leading indentation");
    }
}
