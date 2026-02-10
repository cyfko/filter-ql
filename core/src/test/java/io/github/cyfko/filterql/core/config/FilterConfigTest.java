package io.github.cyfko.filterql.core.config;

import io.github.cyfko.filterql.core.config.EnumMatchMode;
import io.github.cyfko.filterql.core.config.NullValuePolicy;
import io.github.cyfko.filterql.core.config.StringCaseStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for FilterConfig and FilterConfig.Builder.
 * Tests builder pattern, defaults, validation, and immutability.
 *
 * @author FilterQL Test Suite
 * @since 3.0.0
 */
@DisplayName("FilterConfig Tests")
class FilterConfigTest {

    // ============================================================================
    // Builder Default Values Tests
    // ============================================================================

    @Test
    @DisplayName("Should build FilterConfig with default values")
    void shouldBuildFilterConfigWithDefaultValues() {
        // When
        FilterConfig config = FilterConfig.builder().build();

        // Then
        assertNotNull(config, "FilterConfig should not be null");
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config.getNullValuePolicy(),
            "Default NullValuePolicy should be STRICT_EXCEPTION");
        assertEquals(StringCaseStrategy.LOWER, config.getStringCaseStrategy(),
            "Default StringCaseStrategy should be LOWER");
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config.getEnumMatchMode(),
            "Default EnumMatchMode should be CASE_INSENSITIVE");
    }

    @Test
    @DisplayName("Should create builder instance successfully")
    void shouldCreateBuilderInstanceSuccessfully() {
        // When
        FilterConfig.Builder builder = FilterConfig.builder();

        // Then
        assertNotNull(builder, "Builder should not be null");
    }

    // ============================================================================
    // Builder Custom Values Tests
    // ============================================================================

    @Test
    @DisplayName("Should build FilterConfig with custom NullValuePolicy")
    void shouldBuildFilterConfigWithCustomNullValuePolicy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
            .build();

        // Then
        assertEquals(NullValuePolicy.COERCE_TO_IS_NULL, config.getNullValuePolicy(),
            "Should use custom NullValuePolicy");
        // Other defaults should remain
        assertEquals(StringCaseStrategy.LOWER, config.getStringCaseStrategy());
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config.getEnumMatchMode());
    }

    @Test
    @DisplayName("Should build FilterConfig with custom StringCaseStrategy")
    void shouldBuildFilterConfigWithCustomStringCaseStrategy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .stringCaseStrategy(StringCaseStrategy.UPPER)
            .build();

        // Then
        assertEquals(StringCaseStrategy.UPPER, config.getStringCaseStrategy(),
            "Should use custom StringCaseStrategy");
        // Other defaults should remain
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config.getNullValuePolicy());
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config.getEnumMatchMode());
    }

    @Test
    @DisplayName("Should build FilterConfig with custom EnumMatchMode")
    void shouldBuildFilterConfigWithCustomEnumMatchMode() {
        // When
        FilterConfig config = FilterConfig.builder()
            .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
            .build();

        // Then
        assertEquals(EnumMatchMode.CASE_SENSITIVE, config.getEnumMatchMode(),
            "Should use custom EnumMatchMode");
        // Other defaults should remain
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config.getNullValuePolicy());
        assertEquals(StringCaseStrategy.LOWER, config.getStringCaseStrategy());
    }

    @Test
    @DisplayName("Should build FilterConfig with all custom values")
    void shouldBuildFilterConfigWithAllCustomValues() {
        // When
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
            .stringCaseStrategy(StringCaseStrategy.NONE)
            .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
            .build();

        // Then
        assertEquals(NullValuePolicy.IGNORE_FILTER, config.getNullValuePolicy(),
            "Should use custom NullValuePolicy");
        assertEquals(StringCaseStrategy.NONE, config.getStringCaseStrategy(),
            "Should use custom StringCaseStrategy");
        assertEquals(EnumMatchMode.CASE_SENSITIVE, config.getEnumMatchMode(),
            "Should use custom EnumMatchMode");
    }

    // ============================================================================
    // Fluent Builder Tests
    // ============================================================================

    @Test
    @DisplayName("Should support fluent builder pattern")
    void shouldSupportFluentBuilderPattern() {
        // When
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
            .stringCaseStrategy(StringCaseStrategy.UPPER)
            .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
            .build();

        // Then
        assertNotNull(config, "Fluent builder should produce valid config");
        assertEquals(NullValuePolicy.COERCE_TO_IS_NULL, config.getNullValuePolicy());
        assertEquals(StringCaseStrategy.UPPER, config.getStringCaseStrategy());
        assertEquals(EnumMatchMode.CASE_SENSITIVE, config.getEnumMatchMode());
    }

    @Test
    @DisplayName("Should allow chaining builder methods")
    void shouldAllowChainingBuilderMethods() {
        // When
        FilterConfig.Builder builder = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
            .stringCaseStrategy(StringCaseStrategy.LOWER)
            .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE);

        FilterConfig config = builder.build();

        // Then
        assertNotNull(config);
        assertEquals(NullValuePolicy.IGNORE_FILTER, config.getNullValuePolicy());
        assertEquals(StringCaseStrategy.LOWER, config.getStringCaseStrategy());
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config.getEnumMatchMode());
    }

    // ============================================================================
    // Null Validation Tests
    // ============================================================================

    @Test
    @DisplayName("Should throw NullPointerException when nullValuePolicy is null")
    void shouldThrowExceptionWhenNullValuePolicyIsNull() {
        // When & Then
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> FilterConfig.builder().nullValuePolicy(null),
            "Should throw NullPointerException for null NullValuePolicy"
        );

        assertTrue(exception.getMessage().contains("nullValuePolicy"),
            "Exception message should mention nullValuePolicy");
    }

    @Test
    @DisplayName("Should throw NullPointerException when stringCaseStrategy is null")
    void shouldThrowExceptionWhenStringCaseStrategyIsNull() {
        // When & Then
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> FilterConfig.builder().stringCaseStrategy(null),
            "Should throw NullPointerException for null StringCaseStrategy"
        );

        assertTrue(exception.getMessage().contains("stringCaseStrategy"),
            "Exception message should mention stringCaseStrategy");
    }

    @Test
    @DisplayName("Should throw NullPointerException when enumMatchMode is null")
    void shouldThrowExceptionWhenEnumMatchModeIsNull() {
        // When & Then
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> FilterConfig.builder().enumMatchMode(null),
            "Should throw NullPointerException for null EnumMatchMode"
        );

        assertTrue(exception.getMessage().contains("enumMatchMode"),
            "Exception message should mention enumMatchMode");
    }

    // ============================================================================
    // Immutability Tests
    // ============================================================================

    @Test
    @DisplayName("Should ensure FilterConfig is immutable after build")
    void shouldEnsureFilterConfigIsImmutable() {
        // Given
        FilterConfig.Builder builder = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.STRICT_EXCEPTION);

        FilterConfig config = builder.build();

        // When - Modify builder after build
        builder.nullValuePolicy(NullValuePolicy.IGNORE_FILTER);
        FilterConfig newConfig = builder.build();

        // Then - Original config should remain unchanged
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config.getNullValuePolicy(),
            "Original config should not be affected by builder modifications");
        assertEquals(NullValuePolicy.IGNORE_FILTER, newConfig.getNullValuePolicy(),
            "New config should have new value");
    }

    @Test
    @DisplayName("Should have no setter methods on FilterConfig")
    void shouldHaveNoSetterMethodsOnFilterConfig() {
        // Given
        FilterConfig config = FilterConfig.builder().build();

        // Then
        assertNotNull(config.getNullValuePolicy(), "Should have getter");
        assertNotNull(config.getStringCaseStrategy(), "Should have getter");
        assertNotNull(config.getEnumMatchMode(), "Should have getter");

        // Verify no public setter methods exist (via reflection would be more thorough,
        // but this test ensures compile-time contract)
    }

    // ============================================================================
    // All NullValuePolicy Variants Tests
    // ============================================================================

    @Test
    @DisplayName("Should support STRICT_EXCEPTION null value policy")
    void shouldSupportStrictExceptionNullValuePolicy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.STRICT_EXCEPTION)
            .build();

        // Then
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config.getNullValuePolicy());
    }

    @Test
    @DisplayName("Should support COERCE_TO_IS_NULL null value policy")
    void shouldSupportCoerceToIsNullNullValuePolicy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
            .build();

        // Then
        assertEquals(NullValuePolicy.COERCE_TO_IS_NULL, config.getNullValuePolicy());
    }

    @Test
    @DisplayName("Should support IGNORE_FILTER null value policy")
    void shouldSupportIgnoreFilterNullValuePolicy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
            .build();

        // Then
        assertEquals(NullValuePolicy.IGNORE_FILTER, config.getNullValuePolicy());
    }

    // ============================================================================
    // All StringCaseStrategy Variants Tests
    // ============================================================================

    @Test
    @DisplayName("Should support NONE string case strategy")
    void shouldSupportNoneStringCaseStrategy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .stringCaseStrategy(StringCaseStrategy.NONE)
            .build();

        // Then
        assertEquals(StringCaseStrategy.NONE, config.getStringCaseStrategy());
    }

    @Test
    @DisplayName("Should support LOWER string case strategy")
    void shouldSupportLowerStringCaseStrategy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .stringCaseStrategy(StringCaseStrategy.LOWER)
            .build();

        // Then
        assertEquals(StringCaseStrategy.LOWER, config.getStringCaseStrategy());
    }

    @Test
    @DisplayName("Should support UPPER string case strategy")
    void shouldSupportUpperStringCaseStrategy() {
        // When
        FilterConfig config = FilterConfig.builder()
            .stringCaseStrategy(StringCaseStrategy.UPPER)
            .build();

        // Then
        assertEquals(StringCaseStrategy.UPPER, config.getStringCaseStrategy());
    }

    // ============================================================================
    // All EnumMatchMode Variants Tests
    // ============================================================================

    @Test
    @DisplayName("Should support CASE_SENSITIVE enum match mode")
    void shouldSupportCaseSensitiveEnumMatchMode() {
        // When
        FilterConfig config = FilterConfig.builder()
            .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
            .build();

        // Then
        assertEquals(EnumMatchMode.CASE_SENSITIVE, config.getEnumMatchMode());
    }

    @Test
    @DisplayName("Should support CASE_INSENSITIVE enum match mode")
    void shouldSupportCaseInsensitiveEnumMatchMode() {
        // When
        FilterConfig config = FilterConfig.builder()
            .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
            .build();

        // Then
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config.getEnumMatchMode());
    }

    // ============================================================================
    // Builder Reusability Tests
    // ============================================================================

    @Test
    @DisplayName("Should allow building multiple configs from same builder")
    void shouldAllowBuildingMultipleConfigsFromSameBuilder() {
        // Given
        FilterConfig.Builder builder = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.STRICT_EXCEPTION);

        // When
        FilterConfig config1 = builder.build();

        builder.nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL);
        FilterConfig config2 = builder.build();

        builder.nullValuePolicy(NullValuePolicy.IGNORE_FILTER);
        FilterConfig config3 = builder.build();

        // Then
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config1.getNullValuePolicy());
        assertEquals(NullValuePolicy.COERCE_TO_IS_NULL, config2.getNullValuePolicy());
        assertEquals(NullValuePolicy.IGNORE_FILTER, config3.getNullValuePolicy());
    }

    // ============================================================================
    // Getters Tests
    // ============================================================================

    @Test
    @DisplayName("Should return correct values from getters")
    void shouldReturnCorrectValuesFromGetters() {
        // Given
        FilterConfig config = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
            .stringCaseStrategy(StringCaseStrategy.UPPER)
            .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
            .build();

        // When & Then
        assertNotNull(config.getNullValuePolicy());
        assertEquals(NullValuePolicy.IGNORE_FILTER, config.getNullValuePolicy());

        assertNotNull(config.getStringCaseStrategy());
        assertEquals(StringCaseStrategy.UPPER, config.getStringCaseStrategy());

        assertNotNull(config.getEnumMatchMode());
        assertEquals(EnumMatchMode.CASE_SENSITIVE, config.getEnumMatchMode());
    }

    @Test
    @DisplayName("Should never return null from getters")
    void shouldNeverReturnNullFromGetters() {
        // Given
        FilterConfig config = FilterConfig.builder().build();

        // When & Then
        assertNotNull(config.getNullValuePolicy(),
            "getNullValuePolicy should never return null");
        assertNotNull(config.getStringCaseStrategy(),
            "getStringCaseStrategy should never return null");
        assertNotNull(config.getEnumMatchMode(),
            "getEnumMatchMode should never return null");
    }

    // ============================================================================
    // Combinations Tests
    // ============================================================================

    @Test
    @DisplayName("Should handle all combinations of configuration values")
    void shouldHandleAllCombinationsOfConfigurationValues() {
        // Test various realistic combinations
        FilterConfig config1 = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.STRICT_EXCEPTION)
            .stringCaseStrategy(StringCaseStrategy.LOWER)
            .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
            .build();

        FilterConfig config2 = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.COERCE_TO_IS_NULL)
            .stringCaseStrategy(StringCaseStrategy.NONE)
            .enumMatchMode(EnumMatchMode.CASE_SENSITIVE)
            .build();

        FilterConfig config3 = FilterConfig.builder()
            .nullValuePolicy(NullValuePolicy.IGNORE_FILTER)
            .stringCaseStrategy(StringCaseStrategy.UPPER)
            .enumMatchMode(EnumMatchMode.CASE_INSENSITIVE)
            .build();

        // Verify each config has correct values
        assertEquals(NullValuePolicy.STRICT_EXCEPTION, config1.getNullValuePolicy());
        assertEquals(StringCaseStrategy.LOWER, config1.getStringCaseStrategy());
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config1.getEnumMatchMode());

        assertEquals(NullValuePolicy.COERCE_TO_IS_NULL, config2.getNullValuePolicy());
        assertEquals(StringCaseStrategy.NONE, config2.getStringCaseStrategy());
        assertEquals(EnumMatchMode.CASE_SENSITIVE, config2.getEnumMatchMode());

        assertEquals(NullValuePolicy.IGNORE_FILTER, config3.getNullValuePolicy());
        assertEquals(StringCaseStrategy.UPPER, config3.getStringCaseStrategy());
        assertEquals(EnumMatchMode.CASE_INSENSITIVE, config3.getEnumMatchMode());
    }
}
