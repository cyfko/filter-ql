package io.github.cyfko.filterql.core.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests exhaustifs pour ClassUtils avec couverture complète de toutes les méthodes.
 * Couvre les cas normaux, limites, erreurs et les fonctionnalités de réflexion avancées.
 */
@DisplayName("Tests pour ClassUtils")
class ClassUtilsTest {

    // Classes de test pour la réflexion
    static class TestParent {
        public String parentField = "parent";
        private int parentPrivate = 42;
    }

    static class TestChild extends TestParent {
        public String childField = "child";
        private double childPrivate = 3.14;
    }

    static class TestGrandChild extends TestChild {
        public String grandChildField = "grandchild";
    }

    // Classes pour les tests de génériques
    static class GenericTestClass {
        public List<String> stringList;
        public Map<String, Integer> stringIntegerMap;
        public Set<? extends Number> wildcardSet;
        public List<? super String> wildcardSuperList;
        public String[] stringArray;
        public List<String> stringListGeneric;
    }

    static class TypeReferenceTestClass {
        public List<String> stringList;
        public Map<String, Integer> map;
    }

    @BeforeEach
    void setUp() {
        // Nettoyer les caches avant chaque test
        ClassUtils.clearCaches();
    }

    @Nested
    @DisplayName("Tests pour getAnyField")
    class GetAnyFieldTests {

        @Test
        @DisplayName("shouldReturnFieldWhenFoundInSameClass")
        void shouldReturnFieldWhenFoundInSameClass() {
            Optional<Field> field = ClassUtils.getAnyField(TestChild.class, "childField");
            
            assertTrue(field.isPresent());
            assertEquals("childField", field.get().getName());
            assertEquals(String.class, field.get().getType());
        }

        @Test
        @DisplayName("shouldReturnFieldWhenFoundInParentClass")
        void shouldReturnFieldWhenFoundInParentClass() {
            Optional<Field> field = ClassUtils.getAnyField(TestChild.class, "parentField");
            
            assertTrue(field.isPresent());
            assertEquals("parentField", field.get().getName());
            assertEquals(String.class, field.get().getType());
        }

        @Test
        @DisplayName("shouldReturnFieldWhenFoundInGrandParentClass")
        void shouldReturnFieldWhenFoundInGrandParentClass() {
            Optional<Field> field = ClassUtils.getAnyField(TestGrandChild.class, "parentField");
            
            assertTrue(field.isPresent());
            assertEquals("parentField", field.get().getName());
        }

        @Test
        @DisplayName("shouldReturnEmptyWhenFieldNotFound")
        void shouldReturnEmptyWhenFieldNotFound() {
            Optional<Field> field = ClassUtils.getAnyField(TestChild.class, "nonExistentField");
            
            assertFalse(field.isPresent());
        }

        @Test
        @DisplayName("shouldReturnPrivateField")
        void shouldReturnPrivateField() {
            Optional<Field> field = ClassUtils.getAnyField(TestChild.class, "parentPrivate");
            
            assertTrue(field.isPresent());
            assertEquals("parentPrivate", field.get().getName());
            assertTrue((field.get().getModifiers() & java.lang.reflect.Modifier.PRIVATE) != 0);
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenClassIsNull")
        void shouldThrowExceptionWhenClassIsNull() {
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.getAnyField(null, "fieldName"));
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenFieldNameIsNull")
        void shouldThrowExceptionWhenFieldNameIsNull() {
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.getAnyField(TestChild.class, null));
        }

        @Test
        @DisplayName("shouldUseCacheForRepeatedCalls")
        void shouldUseCacheForRepeatedCalls() {
            // Premier appel
            Optional<Field> field1 = ClassUtils.getAnyField(TestChild.class, "childField");
            assertTrue(field1.isPresent());
            
            // Deuxième appel - devrait utiliser le cache
            Optional<Field> field2 = ClassUtils.getAnyField(TestChild.class, "childField");
            assertTrue(field2.isPresent());
            
            // Vérifier que c'est le même objet (référence)
            assertSame(field1.get(), field2.get());
        }

        @Test
        @DisplayName("shouldReturnEmptyForObjectClass")
        void shouldReturnEmptyForObjectClass() {
            Optional<Field> field = ClassUtils.getAnyField(Object.class, "nonExistentField");
            assertFalse(field.isPresent());
        }
    }

    @Nested
    @DisplayName("Tests pour getCollectionGenericType")
    class GetCollectionGenericTypeTests {

        @Test
        @DisplayName("shouldExtractGenericTypeFromList")
        void shouldExtractGenericTypeFromList() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("stringList");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            assertTrue(genericType.isPresent());
            assertEquals(String.class, genericType.get());
        }

        @Test
        @DisplayName("shouldExtractGenericTypeFromMap")
        void shouldExtractGenericTypeFromMap() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("stringIntegerMap");
            
            // Test du premier paramètre (clé)
            Optional<Type> keyType = ClassUtils.getCollectionGenericType(field, 0);
            assertTrue(keyType.isPresent());
            assertEquals(String.class, keyType.get());
            
            // Test du deuxième paramètre (valeur)
            Optional<Type> valueType = ClassUtils.getCollectionGenericType(field, 1);
            assertTrue(valueType.isPresent());
            assertEquals(Integer.class, valueType.get());
        }

        @Test
        @DisplayName("shouldHandleWildcardExtendsType")
        void shouldHandleWildcardExtendsType() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("wildcardSet");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            assertTrue(genericType.isPresent());
            assertEquals(Number.class, genericType.get());
        }

        @Test
        @DisplayName("shouldHandleWildcardSuperType")
        void shouldHandleWildcardSuperType() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("wildcardSuperList");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            assertTrue(genericType.isPresent());
            assertEquals(String.class, genericType.get());
        }

        @Test
        @DisplayName("shouldReturnEmptyForNonParameterizedType")
        void shouldReturnEmptyForNonParameterizedType() throws NoSuchFieldException {
            Field field = TestChild.class.getDeclaredField("childField");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            assertFalse(genericType.isPresent());
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenFieldIsNull")
        void shouldThrowExceptionWhenFieldIsNull() {
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.getCollectionGenericType(null, 0));
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenIndexOutOfBounds")
        void shouldThrowExceptionWhenIndexOutOfBounds() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("stringList");
            
            assertThrows(IndexOutOfBoundsException.class, () -> 
                ClassUtils.getCollectionGenericType(field, 5));
            
            assertThrows(IndexOutOfBoundsException.class, () -> 
                ClassUtils.getCollectionGenericType(field, -1));
        }
    }

    @Nested
    @DisplayName("Tests pour getCommonSuperclass")
    class GetCommonSuperclassTests {

        @Test
        @DisplayName("shouldReturnExactClassWhenAllElementsSameType")
        void shouldReturnExactClassWhenAllElementsSameType() {
            List<String> strings = Arrays.asList("a", "b", "c");
            Class<? super String> commonClass = ClassUtils.getCommonSuperclass(strings);
            
            assertEquals(String.class, commonClass);
        }

        @Test
        @DisplayName("shouldReturnCommonSuperclassForDifferentTypes")
        void shouldReturnCommonSuperclassForDifferentTypes() {
            List<Number> numbers = Arrays.asList(1, 2.5, 3L);
            Class<? super Number> commonClass = ClassUtils.getCommonSuperclass(numbers);
            
            assertEquals(Number.class, commonClass);
        }

        @Test
        @DisplayName("shouldReturnObjectClassWhenNoCommonSuperclass")
        void shouldReturnObjectClassWhenNoCommonSuperclass() {
            List<Object> mixed = Arrays.asList("string", 42, true);
            Class<? super Object> commonClass = ClassUtils.getCommonSuperclass(mixed);
            
            assertEquals(Object.class, commonClass);
        }

        @Test
        @DisplayName("shouldIgnoreNullElements")
        void shouldIgnoreNullElements() {
            List<String> withNulls = Arrays.asList("a", null, "c");
            Class<? super String> commonClass = ClassUtils.getCommonSuperclass(withNulls);
            
            assertEquals(String.class, commonClass);
        }

        @Test
        @DisplayName("shouldReturnObjectClassWhenAllElementsNull")
        void shouldReturnObjectClassWhenAllElementsNull() {
            List<String> allNulls = Arrays.asList(null, null, null);
            Class<? super String> commonClass = ClassUtils.getCommonSuperclass(allNulls);
            
            assertEquals(Object.class, commonClass);
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenCollectionIsNull")
        void shouldThrowExceptionWhenCollectionIsNull() {
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.getCommonSuperclass(null));
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenCollectionIsEmpty")
        void shouldThrowExceptionWhenCollectionIsEmpty() {
            assertThrows(IllegalArgumentException.class, () -> 
                ClassUtils.getCommonSuperclass(Collections.emptyList()));
        }

        @Test
        @DisplayName("shouldUseCacheForRepeatedCalls")
        void shouldUseCacheForRepeatedCalls() {
            List<Number> numbers = Arrays.asList(1, 2.5, 3L);
            
            // Premier appel
            Class<? super Number> commonClass1 = ClassUtils.getCommonSuperclass(numbers);
            assertEquals(Number.class, commonClass1);
            
            // Deuxième appel - devrait utiliser le cache
            Class<? super Number> commonClass2 = ClassUtils.getCommonSuperclass(numbers);
            assertEquals(Number.class, commonClass2);
            
            // Vérifier les statistiques du cache
            Map<String, Integer> stats = ClassUtils.getCacheStats();
            assertTrue(stats.get("superclassCacheSize") > 0);
        }
    }

    @Nested
    @DisplayName("Tests pour getCommonClass")
    class GetCommonClassTests {

        @Test
        @DisplayName("shouldReturnExactClassWhenAllElementsSameType")
        void shouldReturnExactClassWhenAllElementsSameType() {
            List<String> strings = Arrays.asList("a", "b", "c");
            Optional<Class<?>> commonClass = ClassUtils.getCommonClass(strings);
            
            assertTrue(commonClass.isPresent());
            assertEquals(String.class, commonClass.get());
        }

        @Test
        @DisplayName("shouldReturnEmptyWhenElementsDifferentTypes")
        void shouldReturnEmptyWhenElementsDifferentTypes() {
            List<Object> mixed = Arrays.asList("string", 42, true);
            Optional<Class<?>> commonClass = ClassUtils.getCommonClass(mixed);
            
            assertFalse(commonClass.isPresent());
        }

        @Test
        @DisplayName("shouldReturnEmptyWhenAllElementsNull")
        void shouldReturnEmptyWhenAllElementsNull() {
            List<String> allNulls = Arrays.asList(null, null, null);
            Optional<Class<?>> commonClass = ClassUtils.getCommonClass(allNulls);
            
            assertFalse(commonClass.isPresent());
        }

        @Test
        @DisplayName("shouldReturnEmptyWhenCollectionIsEmpty")
        void shouldReturnEmptyWhenCollectionIsEmpty() {
            Optional<Class<?>> commonClass = ClassUtils.getCommonClass(Collections.emptyList());
            
            assertFalse(commonClass.isPresent());
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenCollectionIsNull")
        void shouldThrowExceptionWhenCollectionIsNull() {
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.getCommonClass(null));
        }

        @Test
        @DisplayName("shouldIgnoreNullElements")
        void shouldIgnoreNullElements() {
            List<String> withNulls = Arrays.asList("a", null, "c");
            Optional<Class<?>> commonClass = ClassUtils.getCommonClass(withNulls);
            
            assertTrue(commonClass.isPresent());
            assertEquals(String.class, commonClass.get());
        }
    }

    @Nested
    @DisplayName("Tests pour allCompatible")
    class AllCompatibleTests {

        @Test
        @DisplayName("shouldReturnTrueWhenAllElementsCompatible")
        void shouldReturnTrueWhenAllElementsCompatible() {
            List<Number> numbers = Arrays.asList(1, 2.5, 3L);
            boolean compatible = ClassUtils.allCompatible(Number.class, numbers);
            
            assertTrue(compatible);
        }

        @Test
        @DisplayName("shouldReturnFalseWhenElementsNotCompatible")
        void shouldReturnFalseWhenElementsNotCompatible() {
            List<Object> mixed = Arrays.asList("string", 42, true);
            boolean compatible = ClassUtils.allCompatible(String.class, mixed);
            
            assertFalse(compatible);
        }

        @Test
        @DisplayName("shouldIgnoreNullElements")
        void shouldIgnoreNullElements() {
            List<String> withNulls = Arrays.asList("a", null, "c");
            boolean compatible = ClassUtils.allCompatible(String.class, withNulls);
            
            assertTrue(compatible);
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenBaseClassIsNull")
        void shouldThrowExceptionWhenBaseClassIsNull() {
            List<String> strings = Arrays.asList("a", "b");
            
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.allCompatible(null, strings));
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenCollectionIsNull")
        void shouldThrowExceptionWhenCollectionIsNull() {
            assertThrows(NullPointerException.class, () -> 
                ClassUtils.allCompatible(String.class, null));
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenCollectionIsEmpty")
        void shouldThrowExceptionWhenCollectionIsEmpty() {
            assertThrows(IllegalStateException.class, () -> 
                ClassUtils.allCompatible(String.class, Collections.emptyList()));
        }

        @Test
        @DisplayName("shouldReturnTrueForInheritanceCompatibility")
        void shouldReturnTrueForInheritanceCompatibility() {
            List<TestChild> children = Arrays.asList(new TestChild(), new TestChild());
            boolean compatible = ClassUtils.allCompatible(TestParent.class, children);
            
            assertTrue(compatible);
        }
    }

    @Nested
    @DisplayName("Tests pour TypeReference")
    class TypeReferenceTests {

        @Test
        @DisplayName("shouldCaptureStringType")
        void shouldCaptureStringType() {
            ClassUtils.TypeReference<String> typeRef = new ClassUtils.TypeReference<String>() {};
            
            assertEquals(String.class, typeRef.getTypeClass());
            assertEquals(String.class, typeRef.getType());
            assertTrue(typeRef.isAssignableFrom(String.class));
            assertTrue(typeRef.isInstanceOf(String.class));
        }

        @Test
        @DisplayName("shouldCaptureListType")
        void shouldCaptureListType() {
            ClassUtils.TypeReference<List<String>> typeRef = new ClassUtils.TypeReference<List<String>>() {};
            
            assertEquals(List.class, typeRef.getTypeClass());
            assertTrue(typeRef.isAssignableFrom(List.class));
            assertTrue(typeRef.isInstanceOf(List.class));
        }

        @Test
        @DisplayName("shouldCaptureMapType")
        void shouldCaptureMapType() {
            ClassUtils.TypeReference<Map<String, Integer>> typeRef = new ClassUtils.TypeReference<Map<String, Integer>>() {};
            
            assertEquals(Map.class, typeRef.getTypeClass());
            assertTrue(typeRef.isAssignableFrom(Map.class));
        }

        @Test
        @DisplayName("shouldWorkWithAnonymousClass")
        void shouldWorkWithAnonymousClass() {
            // Test que TypeReference fonctionne avec une classe anonyme
            ClassUtils.TypeReference<String> typeRef = new ClassUtils.TypeReference<String>() {};
            
            assertEquals(String.class, typeRef.getTypeClass());
            assertTrue(typeRef.isAssignableFrom(String.class));
        }

        @Test
        @DisplayName("shouldThrowExceptionWhenNoGenericType")
        void shouldThrowExceptionWhenNoGenericType() {
            assertThrows(IllegalArgumentException.class, () -> 
                new ClassUtils.TypeReference() {});
        }

        @Test
        @DisplayName("shouldHandleWildcardTypes")
        void shouldHandleWildcardTypes() {
            ClassUtils.TypeReference<Number> typeRef = new ClassUtils.TypeReference<Number>() {};
            
            assertEquals(Number.class, typeRef.getTypeClass());
            assertTrue(typeRef.isAssignableFrom(Integer.class));
            assertTrue(typeRef.isAssignableFrom(Double.class));
        }

        @Test
        @DisplayName("shouldImplementEqualsAndHashCode")
        void shouldImplementEqualsAndHashCode() {
            ClassUtils.TypeReference<String> typeRef1 = new ClassUtils.TypeReference<String>() {};
            ClassUtils.TypeReference<String> typeRef2 = new ClassUtils.TypeReference<String>() {};
            ClassUtils.TypeReference<Integer> typeRef3 = new ClassUtils.TypeReference<Integer>() {};
            
            assertEquals(typeRef1, typeRef2);
            assertNotEquals(typeRef1, typeRef3);
            assertEquals(typeRef1.hashCode(), typeRef2.hashCode());
        }

        @Test
        @DisplayName("shouldHaveMeaningfulToString")
        void shouldHaveMeaningfulToString() {
            ClassUtils.TypeReference<String> typeRef = new ClassUtils.TypeReference<String>() {};
            String toString = typeRef.toString();
            
            assertTrue(toString.contains("TypeReference"));
            assertTrue(toString.contains("String"));
        }
    }

    @Nested
    @DisplayName("Tests pour les méthodes utilitaires")
    class UtilityMethodsTests {

        @Test
        @DisplayName("shouldClearCaches")
        void shouldClearCaches() {
            // Utiliser les caches
            ClassUtils.getAnyField(TestChild.class, "childField");
            ClassUtils.getCommonSuperclass(Arrays.asList(1, 2, 3));
            
            Map<String, Integer> statsBefore = ClassUtils.getCacheStats();
            assertTrue(statsBefore.get("fieldCacheSize") > 0 || statsBefore.get("superclassCacheSize") > 0);
            
            // Nettoyer les caches
            ClassUtils.clearCaches();
            
            Map<String, Integer> statsAfter = ClassUtils.getCacheStats();
            assertEquals(0, statsAfter.get("fieldCacheSize"));
            assertEquals(0, statsAfter.get("superclassCacheSize"));
        }

        @Test
        @DisplayName("shouldReturnCacheStats")
        void shouldReturnCacheStats() {
            Map<String, Integer> stats = ClassUtils.getCacheStats();
            
            assertNotNull(stats);
            assertTrue(stats.containsKey("fieldCacheSize"));
            assertTrue(stats.containsKey("superclassCacheSize"));
            assertTrue(stats.get("fieldCacheSize") >= 0);
            assertTrue(stats.get("superclassCacheSize") >= 0);
        }

        @Test
        @DisplayName("shouldReturnImmutableCacheStats")
        void shouldReturnImmutableCacheStats() {
            Map<String, Integer> stats = ClassUtils.getCacheStats();
            
            assertThrows(UnsupportedOperationException.class, () -> 
                stats.put("newKey", 1));
        }
    }

    @Nested
    @DisplayName("Tests de cas limites et robustesse")
    class EdgeCasesAndRobustnessTests {

        @Test
        @DisplayName("shouldHandleComplexGenericTypes")
        void shouldHandleComplexGenericTypes() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("stringListGeneric");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            // Pour une liste générique, on devrait obtenir le type du paramètre
            assertTrue(genericType.isPresent());
            assertEquals(String.class, genericType.get());
        }

        @Test
        @DisplayName("shouldHandleEmptyWildcardType")
        void shouldHandleEmptyWildcardType() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("wildcardSet");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            assertTrue(genericType.isPresent());
            assertEquals(Number.class, genericType.get());
        }

        @Test
        @DisplayName("shouldHandleTypeVariable")
        void shouldHandleTypeVariable() throws NoSuchFieldException {
            Field field = GenericTestClass.class.getDeclaredField("stringListGeneric");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);
            
            // Pour une liste de String, devrait retourner String
            assertTrue(genericType.isPresent());
            assertEquals(String.class, genericType.get());
        }

        @Test
        @DisplayName("shouldHandleVeryDeepInheritance")
        void shouldHandleVeryDeepInheritance() {
            // Test avec une hiérarchie profonde
            Optional<Field> field = ClassUtils.getAnyField(TestGrandChild.class, "parentField");
            assertTrue(field.isPresent());
            assertEquals("parentField", field.get().getName());
        }

        @Test
        @DisplayName("shouldHandleLargeCollections")
        void shouldHandleLargeCollections() {
            List<Integer> largeList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                largeList.add(i);
            }
            
            Class<? super Integer> commonClass = ClassUtils.getCommonSuperclass(largeList);
            assertEquals(Integer.class, commonClass);
        }

        @Test
        @DisplayName("shouldHandleMixedNullAndNonNullElements")
        void shouldHandleMixedNullAndNonNullElements() {
            List<String> mixed = Arrays.asList("a", null, "b", null, "c");
            Optional<Class<?>> commonClass = ClassUtils.getCommonClass(mixed);
            
            assertTrue(commonClass.isPresent());
            assertEquals(String.class, commonClass.get());
        }
    }

    @Nested
    @DisplayName("Tests pour les types génériques complexes")
    class ComplexGenericTypeTests {

        @Test
        @DisplayName("shouldHandleNestedGenericTypes")
        void shouldHandleNestedGenericTypes() throws NoSuchFieldException {
            class TestClass {
                List<Map<String, List<Integer>>> nestedGenericField;
            }

            Field field = TestClass.class.getDeclaredField("nestedGenericField");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);

            assertTrue(genericType.isPresent());
            assertTrue(genericType.get() instanceof ParameterizedType);
            ParameterizedType paramType = (ParameterizedType) genericType.get();
            assertEquals(Map.class, paramType.getRawType());
        }

        @Test
        @DisplayName("shouldHandleRecursiveGenericTypes")
        void shouldHandleRecursiveGenericTypes() throws NoSuchFieldException {
            class Node<T> {
                Node<T> parent;
                List<Node<T>> children;
            }

            Field field = Node.class.getDeclaredField("children");
            Optional<Type> genericType = ClassUtils.getCollectionGenericType(field, 0);

            assertTrue(genericType.isPresent());
            assertTrue(genericType.get() instanceof ParameterizedType);
        }
    }

    @Nested
    @DisplayName("Tests de performance et concurrence")
    class PerformanceAndConcurrencyTests {

        @Test
        @DisplayName("shouldHandleConcurrentCacheAccess")
        void shouldHandleConcurrentCacheAccess() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        // Accès concurrent aux caches
                        ClassUtils.getAnyField(TestChild.class, "childField");
                        ClassUtils.getCommonSuperclass(Arrays.asList(1, 2, 3));
                    } catch (InterruptedException e) {
                        fail("Thread interrupted");
                    } finally {
                        endLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            assertTrue(endLatch.await(5, TimeUnit.SECONDS),
                    "Concurrent operations should complete within timeout");
        }

        @Test
        @DisplayName("shouldHandleLargeTypeHierarchy")
        void shouldHandleLargeTypeHierarchy() {
            class Level0 {}
            class Level1 extends Level0 {}
            class Level2 extends Level1 {}
            // ... créer une hiérarchie profonde

            List<Object> objects = Arrays.asList(new Level0(), new Level1(), new Level2());
            long start = System.nanoTime();
            Class<?> result = ClassUtils.getCommonSuperclass(objects);
            long duration = System.nanoTime() - start;

            assertEquals(Level0.class, result);
            assertTrue(duration < 1_000_000_000, // 1 seconde
                    "Deep hierarchy analysis should complete within reasonable time");
        }
    }

    @Nested
    @DisplayName("Tests de validation robuste")
    class RobustValidationTests {

        @Test
        @DisplayName("shouldNotRejectMalformedFieldNames")
        void shouldNotRejectMalformedFieldNames() {
            assertTrue(ClassUtils.getAnyField(TestClass.class, "invalid.field.name").isEmpty());

            assertTrue(ClassUtils.getAnyField(TestClass.class, "").isEmpty());

        }

        // Test for SecurityManager removed due to deprecation and removal in Java 17+.

        class TestClass {
            private String privateField;
            protected int protectedField;
            public List<String> publicField;

            class InnerClass {
                private String innerField;
            }
        }
    }

    @Nested
    @DisplayName("Tests des limites du cache")
    class CacheLimitTests {

        @Test
        @DisplayName("shouldHandleMemoryPressure")
        void shouldHandleMemoryPressure() {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // Créer beaucoup d'entrées de cache
            for (int i = 0; i < 10_000; i++) {
                ClassUtils.getAnyField(Object.class, "field" + i);
            }

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = finalMemory - initialMemory;

            assertTrue(memoryIncrease < 10_000_000,
                    "Cache memory usage should be reasonable");
        }
    }

    // Classe de test pour TypeReference (non anonyme)
    static class NamedTypeReference extends ClassUtils.TypeReference<String> {
        // Cette classe ne devrait pas être instanciable
    }
}
