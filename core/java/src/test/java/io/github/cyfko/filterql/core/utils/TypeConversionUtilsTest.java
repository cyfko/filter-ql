package io.github.cyfko.filterql.core.utils;

import io.github.cyfko.filterql.core.config.EnumMatchMode;
import io.github.cyfko.filterql.core.config.StringCaseStrategy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for TypeConversionUtils.
 * Tests cover all supported type conversions, edge cases, and error scenarios.
 */
class TypeConversionUtilsTest {

    // Test enum for conversion tests
    enum TestStatus {
        ACTIVE, PENDING, INACTIVE
    }

    @Nested
    class NumericConversionTests {

        @Test
        void shouldConvertStringToInteger() {
            Object result = TypeConversionUtils.convertValue(Integer.class, "42", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(42, result);
        }

        @Test
        void shouldConvertStringToLong() {
            Object result = TypeConversionUtils.convertValue(Long.class, "9876543210", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(9876543210L, result);
        }

        @Test
        void shouldConvertStringToDouble() {
            Object result = TypeConversionUtils.convertValue(Double.class, "3.14159", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(3.14159, result);
        }

        @Test
        void shouldConvertStringToFloat() {
            Object result = TypeConversionUtils.convertValue(Float.class, "2.718", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(2.718f, result);
        }

        @Test
        void shouldConvertNumberToNumber() {
            Object result = TypeConversionUtils.convertValue(Integer.class, 42L, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(42, result);
        }

        @Test
        void shouldConvertToBigDecimal() {
            Object result = TypeConversionUtils.convertValue(BigDecimal.class, "123.456", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(new BigDecimal("123.456"), result);
        }

        @Test
        void shouldConvertToBigInteger() {
            Object result = TypeConversionUtils.convertValue(BigInteger.class, "999999999999", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(new BigInteger("999999999999"), result);
        }

        @Test
        void shouldConvertPrimitiveTypes() {
            int intResult = (int) TypeConversionUtils.convertValue(int.class, "10", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(10, intResult);

            long longResult = (long) TypeConversionUtils.convertValue(long.class, 100, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(100L, longResult);

            double doubleResult = (double) TypeConversionUtils.convertValue(double.class, "5.5", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(5.5, doubleResult);
        }

        @Test
        void shouldThrowExceptionForInvalidNumericString() {
            assertThrows(IllegalArgumentException.class, () ->
                    TypeConversionUtils.convertValue(Integer.class, "not-a-number", EnumMatchMode.CASE_SENSITIVE)
            );
        }
    }

    @Nested
    class EnumConversionTests {

        @Test
        void shouldConvertExactEnumName() {
            Object result = TypeConversionUtils.convertValue(TestStatus.class, "ACTIVE", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(TestStatus.ACTIVE, result);
        }

        @Test
        void shouldConvertCaseInsensitiveEnumName() {
            Object result = TypeConversionUtils.convertValue(TestStatus.class, "active", EnumMatchMode.CASE_INSENSITIVE);
            assertEquals(TestStatus.ACTIVE, result);
        }

        @Test
        void shouldConvertMixedCaseEnum() {
            Object result = TypeConversionUtils.convertValue(TestStatus.class, "PeNdInG", EnumMatchMode.CASE_INSENSITIVE);
            assertEquals(TestStatus.PENDING, result);
        }

        @Test
        void shouldThrowExceptionForInvalidEnum() {
            assertThrows(IllegalArgumentException.class, () ->
                    TypeConversionUtils.convertValue(TestStatus.class, "UNKNOWN", EnumMatchMode.CASE_SENSITIVE)
            );
        }

        @Test
        void shouldThrowExceptionForCaseMismatchWithExactMode() {
            assertThrows(IllegalArgumentException.class, () ->
                    TypeConversionUtils.convertValue(TestStatus.class, "active", EnumMatchMode.CASE_SENSITIVE)
            );
        }
    }

    @Nested
    class BooleanConversionTests {

        @Test
        void shouldConvertTrueLiterals() {
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, "true", EnumMatchMode.CASE_SENSITIVE));
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, "TRUE", EnumMatchMode.CASE_SENSITIVE));
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, "1", EnumMatchMode.CASE_SENSITIVE));
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, "yes", EnumMatchMode.CASE_SENSITIVE));
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, "oui", EnumMatchMode.CASE_SENSITIVE));
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, "y", EnumMatchMode.CASE_SENSITIVE));
        }

        @Test
        void shouldConvertFalseLiterals() {
            assertFalse((Boolean) TypeConversionUtils.convertValue(Boolean.class, "false", EnumMatchMode.CASE_SENSITIVE));
            assertFalse((Boolean) TypeConversionUtils.convertValue(Boolean.class, "0", EnumMatchMode.CASE_SENSITIVE));
            assertFalse((Boolean) TypeConversionUtils.convertValue(Boolean.class, "no", EnumMatchMode.CASE_SENSITIVE));
        }

        @Test
        void shouldConvertNumericToBoolean() {
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, 1, EnumMatchMode.CASE_SENSITIVE));
            assertFalse((Boolean) TypeConversionUtils.convertValue(Boolean.class, 0, EnumMatchMode.CASE_SENSITIVE));
            assertTrue((Boolean) TypeConversionUtils.convertValue(Boolean.class, 42, EnumMatchMode.CASE_SENSITIVE));
        }

        @Test
        void shouldConvertBooleanPrimitive() {
            boolean result = (boolean) TypeConversionUtils.convertValue(boolean.class, "true", EnumMatchMode.CASE_SENSITIVE);
            assertTrue(result);
        }
    }

    @Nested
    class DateTimeConversionTests {

        @Test
        void shouldConvertStringToLocalDate() {
            LocalDate result = (LocalDate) TypeConversionUtils.convertValue(LocalDate.class, "2024-01-15", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(LocalDate.of(2024, 1, 15), result);
        }

        @Test
        void shouldConvertTimestampToLocalDate() {
            long timestamp = 1640000000000L; // 2021-12-20
            LocalDate result = (LocalDate) TypeConversionUtils.convertValue(LocalDate.class, timestamp, EnumMatchMode.CASE_SENSITIVE);
            assertNotNull(result);
            assertEquals(2021, result.getYear());
            assertEquals(12, result.getMonthValue());
        }

        @Test
        void shouldConvertStringToLocalDateTime() {
            LocalDateTime result = (LocalDateTime) TypeConversionUtils.convertValue(
                    LocalDateTime.class, "2024-01-15T10:30:00", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30, 0), result);
        }

        @Test
        void shouldConvertStringToLocalTime() {
            LocalTime result = (LocalTime) TypeConversionUtils.convertValue(LocalTime.class, "14:30:45", EnumMatchMode.CASE_SENSITIVE);
            assertEquals(LocalTime.of(14, 30, 45), result);
        }

        @Test
        void shouldConvertTimestampToInstant() {
            long timestamp = 1640000000000L;
            Instant result = (Instant) TypeConversionUtils.convertValue(Instant.class, timestamp, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(Instant.ofEpochMilli(timestamp), result);
        }

        @Test
        void shouldConvertStringToInstant() {
            String isoString = "2024-01-15T10:30:00Z";
            Instant result = (Instant) TypeConversionUtils.convertValue(Instant.class, isoString, EnumMatchMode.CASE_SENSITIVE);
            assertNotNull(result);
        }

        @Test
        void shouldConvertTimestampToZonedDateTime() {
            long timestamp = 1640000000000L;
            ZonedDateTime result = (ZonedDateTime) TypeConversionUtils.convertValue(
                    ZonedDateTime.class, timestamp, EnumMatchMode.CASE_SENSITIVE);
            assertNotNull(result);
        }

        @Test
        void shouldConvertToJavaUtilDate() {
            long timestamp = 1640000000000L;
            Date result = (Date) TypeConversionUtils.convertValue(Date.class, timestamp, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(new Date(timestamp), result);
        }

        @Test
        void shouldConvertLocalDateTimeToDate() {
            LocalDateTime ldt = LocalDateTime.of(2024, 1, 15, 10, 30);
            Date result = (Date) TypeConversionUtils.convertValue(Date.class, ldt, EnumMatchMode.CASE_SENSITIVE);
            assertNotNull(result);
        }
    }

    @Nested
    class UUIDConversionTests {

        @Test
        void shouldConvertStringToUUID() {
            String uuidString = "550e8400-e29b-41d4-a716-446655440000";
            UUID result = (UUID) TypeConversionUtils.convertValue(UUID.class, uuidString, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(UUID.fromString(uuidString), result);
        }

        @Test
        void shouldReturnSameUUIDIfAlreadyUUID() {
            UUID uuid = UUID.randomUUID();
            UUID result = (UUID) TypeConversionUtils.convertValue(UUID.class, uuid, EnumMatchMode.CASE_SENSITIVE);
            assertSame(uuid, result);
        }

        @Test
        void shouldThrowExceptionForInvalidUUID() {
            assertThrows(IllegalArgumentException.class, () ->
                    TypeConversionUtils.convertValue(UUID.class, "not-a-uuid", EnumMatchMode.CASE_SENSITIVE)
            );
        }
    }

    @Nested
    class StringConversionTests {

        @Test
        void shouldConvertAnyObjectToString() {
            assertEquals("42", TypeConversionUtils.convertValue(String.class, 42, EnumMatchMode.CASE_SENSITIVE));
            assertEquals("true", TypeConversionUtils.convertValue(String.class, true, EnumMatchMode.CASE_SENSITIVE));
            assertEquals("ACTIVE", TypeConversionUtils.convertValue(String.class, TestStatus.ACTIVE, EnumMatchMode.CASE_SENSITIVE));
        }
    }

    @Nested
    class CollectionConversionTests {

        @Test
        void shouldConvertListToTypedCollection() {
            List<String> input = Arrays.asList("1", "2", "3");
            List<?> result = TypeConversionUtils.convertToTypedCollection(input, Integer.class, EnumMatchMode.CASE_SENSITIVE);

            assertEquals(3, result.size());
            assertEquals(1, result.get(0));
            assertEquals(2, result.get(1));
            assertEquals(3, result.get(2));
        }

        @Test
        void shouldConvertArrayToTypedCollection() {
            String[] input = {"10", "20", "30"};
            List<?> result = TypeConversionUtils.convertToTypedCollection(input, Long.class, EnumMatchMode.CASE_SENSITIVE);

            assertEquals(3, result.size());
            assertEquals(10L, result.get(0));
            assertEquals(20L, result.get(1));
            assertEquals(30L, result.get(2));
        }

        @Test
        void shouldConvertCSVStringToTypedCollection() {
            String csv = "tag1, tag2, tag3";
            List<?> result = TypeConversionUtils.convertToTypedCollection(csv, String.class, EnumMatchMode.CASE_SENSITIVE);

            assertEquals(3, result.size());
            assertEquals("tag1", result.get(0));
            assertEquals("tag2", result.get(1));
            assertEquals("tag3", result.get(2));
        }

        @Test
        void shouldConvertCSVNumbersToTypedCollection() {
            String csv = "100,200,300";
            List<?> result = TypeConversionUtils.convertToTypedCollection(csv, Integer.class, EnumMatchMode.CASE_SENSITIVE);

            assertEquals(3, result.size());
            assertEquals(100, result.get(0));
            assertEquals(200, result.get(1));
            assertEquals(300, result.get(2));
        }

        @Test
        void shouldWrapSingleValueInCollection() {
            List<?> result = TypeConversionUtils.convertToTypedCollection("42", Integer.class, EnumMatchMode.CASE_SENSITIVE);

            assertEquals(1, result.size());
            assertEquals(42, result.get(0));
        }

        @Test
        void shouldReturnNullForNullInput() {
            assertNull(TypeConversionUtils.convertToTypedCollection(null, String.class, EnumMatchMode.CASE_SENSITIVE));
        }

        @Test
        void shouldConvertCollectionOfEnums() {
            List<String> input = Arrays.asList("ACTIVE", "pending", "INACTIVE");
            List<?> result = TypeConversionUtils.convertToTypedCollection(
                    input, TestStatus.class, EnumMatchMode.CASE_INSENSITIVE);

            assertEquals(3, result.size());
            assertEquals(TestStatus.ACTIVE, result.get(0));
            assertEquals(TestStatus.PENDING, result.get(1));
            assertEquals(TestStatus.INACTIVE, result.get(2));
        }
    }

    @Nested
    class StringCaseStrategyTests {

        @Test
        void shouldApplyLowerCaseStrategy() {
            Object result = TypeConversionUtils.applyStringCaseStrategy("HELLO WORLD", StringCaseStrategy.LOWER);
            assertEquals("hello world", result);
        }

        @Test
        void shouldApplyUpperCaseStrategy() {
            Object result = TypeConversionUtils.applyStringCaseStrategy("hello world", StringCaseStrategy.UPPER);
            assertEquals("HELLO WORLD", result);
        }

        @Test
        void shouldApplyNoneStrategy() {
            String input = "Mixed Case";
            Object result = TypeConversionUtils.applyStringCaseStrategy(input, StringCaseStrategy.NONE);
            assertEquals(input, result);
        }

        @Test
        void shouldApplyLowerCaseToCollection() {
            List<String> input = Arrays.asList("HELLO", "WORLD");
            Object result = TypeConversionUtils.applyStringCaseStrategy(input, StringCaseStrategy.LOWER);

            assertTrue(result instanceof Collection);
            List<?> resultList = new ArrayList<>((Collection<?>) result);
            assertEquals("hello", resultList.get(0));
            assertEquals("world", resultList.get(1));
        }

        @Test
        void shouldApplyUpperCaseToCollection() {
            List<String> input = Arrays.asList("hello", "world");
            Object result = TypeConversionUtils.applyStringCaseStrategy(input, StringCaseStrategy.UPPER);

            assertTrue(result instanceof Collection);
            List<?> resultList = new ArrayList<>((Collection<?>) result);
            assertEquals("HELLO", resultList.get(0));
            assertEquals("WORLD", resultList.get(1));
        }

        @Test
        void shouldHandleNullValue() {
            assertNull(TypeConversionUtils.applyStringCaseStrategy(null, StringCaseStrategy.LOWER));
        }

        @Test
        void shouldHandleNullStrategy() {
            String input = "Test";
            Object result = TypeConversionUtils.applyStringCaseStrategy(input, null);
            assertEquals(input, result);
        }

        @Test
        void shouldPreserveNonStringInCollection() {
            List<Object> input = Arrays.asList("HELLO", 42, "WORLD");
            Object result = TypeConversionUtils.applyStringCaseStrategy(input, StringCaseStrategy.LOWER);

            List<?> resultList = new ArrayList<>((Collection<?>) result);
            assertEquals("hello", resultList.get(0));
            assertEquals(42, resultList.get(1));
            assertEquals("world", resultList.get(2));
        }
    }

    @Nested
    class EdgeCasesAndErrorTests {

        @Test
        void shouldReturnNullForNullValue() {
            assertNull(TypeConversionUtils.convertValue(String.class, null, EnumMatchMode.CASE_SENSITIVE));
        }

        @Test
        void shouldReturnSameObjectIfTypeMatches() {
            Integer value = 42;
            Object result = TypeConversionUtils.convertValue(Integer.class, value, EnumMatchMode.CASE_SENSITIVE);
            assertSame(value, result);
        }

        @Test
        void shouldThrowExceptionForUnsupportedConversion() {
            assertThrows(IllegalArgumentException.class, () ->
                    TypeConversionUtils.convertValue(java.awt.Point.class, "invalid", EnumMatchMode.CASE_SENSITIVE)
            );
        }

        @Test
        void shouldHandleStringConstructorTypes() {
            // Types with String constructor should work
            String testPath = "test.txt";
            java.io.File file = (java.io.File) TypeConversionUtils.convertValue(
                    java.io.File.class, testPath, EnumMatchMode.CASE_SENSITIVE);
            assertNotNull(file);
            assertEquals(testPath, file.getPath());
        }
    }

    @Nested
    class ComplexScenarioTests {

        @Test
        void shouldConvertNestedCollectionElements() {
            List<String> timestamps = Arrays.asList("1640000000000", "1650000000000");
            List<?> result = TypeConversionUtils.convertToTypedCollection(
                    timestamps, Long.class, EnumMatchMode.CASE_SENSITIVE);

            assertEquals(2, result.size());
            assertTrue(result.get(0) instanceof Long);
            assertTrue(result.get(1) instanceof Long);
        }

        @Test
        void shouldHandleMixedTypeConversions() {
            // String to Integer
            assertEquals(42, TypeConversionUtils.convertValue(Integer.class, "42", EnumMatchMode.CASE_SENSITIVE));

            // Integer to Long
            assertEquals(42L, TypeConversionUtils.convertValue(Long.class, 42, EnumMatchMode.CASE_SENSITIVE));

            // Long to LocalDate
            LocalDate date = (LocalDate) TypeConversionUtils.convertValue(
                    LocalDate.class, 1640000000000L, EnumMatchMode.CASE_SENSITIVE);
            assertNotNull(date);
        }

        @Test
        void shouldConvertEmptyCSVString() {
            List<?> result = TypeConversionUtils.convertToTypedCollection("", String.class, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(1, result.size());
            assertEquals("", result.get(0));
        }

        @Test
        void shouldConvertSingleItemCSV() {
            List<?> result = TypeConversionUtils.convertToTypedCollection("single", String.class, EnumMatchMode.CASE_SENSITIVE);
            assertEquals(1, result.size());
            assertEquals("single", result.get(0));
        }
    }
}
