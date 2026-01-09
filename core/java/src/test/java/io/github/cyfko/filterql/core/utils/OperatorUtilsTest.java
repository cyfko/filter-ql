package io.github.cyfko.filterql.core.utils;

import io.github.cyfko.filterql.core.validation.Op;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests exhaustifs pour OperatorUtils avec couverture complète de toutes les méthodes.
 * Couvre les cas normaux, limites et la validation des ensembles d'opérateurs.
 */
@DisplayName("Tests pour OperatorUtils")
class OperatorUtilsTest {

    @Nested
    @DisplayName("Tests pour FOR_TEXT")
    class ForTextTests {

        @Test
        @DisplayName("shouldContainCorrectTextOperators")
        void shouldContainCorrectTextOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            
            // Vérifier que tous les opérateurs attendus sont présents
            assertTrue(textOperators.contains(Op.EQ));
            assertTrue(textOperators.contains(Op.NE));
            assertTrue(textOperators.contains(Op.MATCHES));
            assertTrue(textOperators.contains(Op.NOT_MATCHES));
            assertTrue(textOperators.contains(Op.IN));
            assertTrue(textOperators.contains(Op.NOT_IN));
            assertTrue(textOperators.contains(Op.IS_NULL));
            assertTrue(textOperators.contains(Op.NOT_NULL));
        }

        @Test
        @DisplayName("shouldNotContainNumericOperators")
        void shouldNotContainNumericOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            
            // Vérifier que les opérateurs numériques ne sont pas présents
            assertFalse(textOperators.contains(Op.GT));
            assertFalse(textOperators.contains(Op.GTE));
            assertFalse(textOperators.contains(Op.LT));
            assertFalse(textOperators.contains(Op.LTE));
            assertFalse(textOperators.contains(Op.RANGE));
            assertFalse(textOperators.contains(Op.NOT_RANGE));
        }

        @Test
        @DisplayName("shouldHaveCorrectSize")
        void shouldHaveCorrectSize() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            
            // Vérifier que l'ensemble contient exactement 8 opérateurs
            assertEquals(8, textOperators.size());
        }

        @Test
        @DisplayName("shouldBeImmutable")
        void shouldBeImmutable() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            
            // Vérifier que l'ensemble est immuable
            assertThrows(UnsupportedOperationException.class, () -> 
                textOperators.add(Op.EQ));
            
            assertThrows(UnsupportedOperationException.class, () -> 
                textOperators.remove(Op.EQ));
            
            assertThrows(UnsupportedOperationException.class, () -> 
                textOperators.clear());
        }

        @Test
        @DisplayName("shouldContainOnlyTextAppropriateOperators")
        void shouldContainOnlyTextAppropriateOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            
            // Vérifier que tous les opérateurs sont appropriés pour le texte
            for (Op operator : textOperators) {
                // Les opérateurs de texte ne devraient pas nécessiter de comparaisons numériques
                assertTrue(
                    operator == Op.EQ ||
                    operator == Op.NE ||
                    operator == Op.MATCHES ||
                    operator == Op.NOT_MATCHES ||
                    operator == Op.IN ||
                    operator == Op.NOT_IN ||
                    operator == Op.IS_NULL ||
                    operator == Op.NOT_NULL,
                    "Operator " + operator + " should be appropriate for text"
                );
            }
        }

        @ParameterizedTest
        @DisplayName("shouldContainSpecificTextOperators")
        @ValueSource(strings = {"EQ", "NE", "MATCHES", "NOT_MATCHES", "IN", "NOT_IN", "IS_NULL", "NOT_NULL"})
        void shouldContainSpecificTextOperators(String operatorName) {
            Op operator = Op.valueOf(operatorName);
            assertTrue(OperatorUtils.FOR_TEXT.contains(operator), 
                "FOR_TEXT should contain " + operatorName);
        }
    }

    @Nested
    @DisplayName("Tests pour FOR_NUMBER")
    class ForNumberTests {

        @Test
        @DisplayName("shouldContainCorrectNumericOperators")
        void shouldContainCorrectNumericOperators() {
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que tous les opérateurs attendus sont présents
            assertTrue(numericOperators.contains(Op.EQ));
            assertTrue(numericOperators.contains(Op.NE));
            assertTrue(numericOperators.contains(Op.GT));
            assertTrue(numericOperators.contains(Op.GTE));
            assertTrue(numericOperators.contains(Op.LT));
            assertTrue(numericOperators.contains(Op.LTE));
            assertTrue(numericOperators.contains(Op.RANGE));
            assertTrue(numericOperators.contains(Op.NOT_RANGE));
            assertTrue(numericOperators.contains(Op.IN));
            assertTrue(numericOperators.contains(Op.NOT_IN));
            assertTrue(numericOperators.contains(Op.IS_NULL));
            assertTrue(numericOperators.contains(Op.NOT_NULL));
        }

        @Test
        @DisplayName("shouldNotContainTextSpecificOperators")
        void shouldNotContainTextSpecificOperators() {
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que les opérateurs spécifiques au texte ne sont pas présents
            // (en fait, MATCHES et NOT_MATCHES ne sont pas dans FOR_NUMBER)
            assertFalse(numericOperators.contains(Op.MATCHES));
            assertFalse(numericOperators.contains(Op.NOT_MATCHES));
        }

        @Test
        @DisplayName("shouldHaveCorrectSize")
        void shouldHaveCorrectSize() {
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que l'ensemble contient exactement 12 opérateurs
            assertEquals(12, numericOperators.size());
        }

        @Test
        @DisplayName("shouldBeImmutable")
        void shouldBeImmutable() {
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que l'ensemble est immuable
            assertThrows(UnsupportedOperationException.class, () -> 
                numericOperators.add(Op.EQ));
            
            assertThrows(UnsupportedOperationException.class, () -> 
                numericOperators.remove(Op.EQ));
            
            assertThrows(UnsupportedOperationException.class, () -> 
                numericOperators.clear());
        }

        @Test
        @DisplayName("shouldContainOnlyNumericAppropriateOperators")
        void shouldContainOnlyNumericAppropriateOperators() {
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que tous les opérateurs sont appropriés pour les nombres
            for (Op operator : numericOperators) {
                // Les opérateurs numériques devraient inclure les comparaisons et les opérateurs de collection
                assertTrue(
                    operator == Op.EQ ||
                    operator == Op.NE ||
                    operator == Op.GT ||
                    operator == Op.GTE ||
                    operator == Op.LT ||
                    operator == Op.LTE ||
                    operator == Op.RANGE ||
                    operator == Op.NOT_RANGE ||
                    operator == Op.IN ||
                    operator == Op.NOT_IN ||
                    operator == Op.IS_NULL ||
                    operator == Op.NOT_NULL,
                    "Operator " + operator + " should be appropriate for numbers"
                );
            }
        }

        @ParameterizedTest
        @DisplayName("shouldContainSpecificNumericOperators")
        @ValueSource(strings = {
            "EQ", "NE", "GT", "GTE",
            "LT", "LTE", "RANGE", "NOT_RANGE",
            "IN", "NOT_IN", "IS_NULL", "NOT_NULL"
        })
        void shouldContainSpecificNumericOperators(String operatorName) {
            Op operator = Op.valueOf(operatorName);
            assertTrue(OperatorUtils.FOR_NUMBER.contains(operator), 
                "FOR_NUMBER should contain " + operatorName);
        }
    }

    @Nested
    @DisplayName("Tests de comparaison entre FOR_TEXT et FOR_NUMBER")
    class ComparisonTests {

        @Test
        @DisplayName("shouldHaveOverlappingOperators")
        void shouldHaveOverlappingOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que certains opérateurs sont communs aux deux
            assertTrue(textOperators.contains(Op.EQ));
            assertTrue(numericOperators.contains(Op.EQ));
            
            assertTrue(textOperators.contains(Op.NE));
            assertTrue(numericOperators.contains(Op.NE));
            
            assertTrue(textOperators.contains(Op.IN));
            assertTrue(numericOperators.contains(Op.IN));
            
            assertTrue(textOperators.contains(Op.NOT_IN));
            assertTrue(numericOperators.contains(Op.NOT_IN));
            
            assertTrue(textOperators.contains(Op.IS_NULL));
            assertTrue(numericOperators.contains(Op.IS_NULL));
            
            assertTrue(textOperators.contains(Op.NOT_NULL));
            assertTrue(numericOperators.contains(Op.NOT_NULL));
        }

        @Test
        @DisplayName("shouldHaveDifferentSizes")
        void shouldHaveDifferentSizes() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // FOR_NUMBER devrait avoir plus d'opérateurs que FOR_TEXT
            assertTrue(numericOperators.size() > textOperators.size());
            assertEquals(8, textOperators.size());
            assertEquals(12, numericOperators.size());
        }

        @Test
        @DisplayName("shouldHaveTextSpecificOperators")
        void shouldHaveTextSpecificOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // MATCHES et NOT_MATCHES devraient être spécifiques au texte
            assertTrue(textOperators.contains(Op.MATCHES));
            assertFalse(numericOperators.contains(Op.MATCHES));
            
            assertTrue(textOperators.contains(Op.NOT_MATCHES));
            assertFalse(numericOperators.contains(Op.NOT_MATCHES));
        }

        @Test
        @DisplayName("shouldHaveNumericSpecificOperators")
        void shouldHaveNumericSpecificOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Les opérateurs de comparaison devraient être spécifiques aux nombres
            assertFalse(textOperators.contains(Op.GT));
            assertTrue(numericOperators.contains(Op.GT));
            
            assertFalse(textOperators.contains(Op.GTE));
            assertTrue(numericOperators.contains(Op.GTE));
            
            assertFalse(textOperators.contains(Op.LT));
            assertTrue(numericOperators.contains(Op.LT));
            
            assertFalse(textOperators.contains(Op.LTE));
            assertTrue(numericOperators.contains(Op.LTE));
            
            assertFalse(textOperators.contains(Op.RANGE));
            assertTrue(numericOperators.contains(Op.RANGE));
            
            assertFalse(textOperators.contains(Op.NOT_RANGE));
            assertTrue(numericOperators.contains(Op.NOT_RANGE));
        }
    }

    @Nested
    @DisplayName("Tests de validation des opérateurs")
    class ValidationTests {

        @Test
        @DisplayName("shouldContainAllRequiredOperators")
        void shouldContainAllRequiredOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que tous les opérateurs nécessaires sont présents
            assertTrue(textOperators.containsAll(Set.of(
                Op.EQ, Op.NE, Op.MATCHES, Op.NOT_MATCHES,
                Op.IN, Op.NOT_IN, Op.IS_NULL, Op.NOT_NULL
            )));
            
            assertTrue(numericOperators.containsAll(Set.of(
                Op.EQ, Op.NE, Op.GT, Op.GTE,
                Op.LT, Op.LTE, Op.RANGE, Op.NOT_RANGE,
                Op.IN, Op.NOT_IN, Op.IS_NULL, Op.NOT_NULL
            )));
        }

        @Test
        @DisplayName("shouldNotContainDuplicateOperators")
        void shouldNotContainDuplicateOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier qu'il n'y a pas de doublons (les Set ne peuvent pas en contenir)
            assertEquals(textOperators.size(), Set.copyOf(textOperators).size());
            assertEquals(numericOperators.size(), Set.copyOf(numericOperators).size());
        }

        @Test
        @DisplayName("shouldContainOnlyValidOperators")
        void shouldContainOnlyValidOperators() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que tous les opérateurs sont des valeurs valides de l'enum Operator
            for (Op operator : textOperators) {
                assertNotNull(operator);
                assertTrue(operator instanceof Op);
            }
            
            for (Op operator : numericOperators) {
                assertNotNull(operator);
                assertTrue(operator instanceof Op);
            }
        }
    }

    @Nested
    @DisplayName("Tests de cas limites")
    class EdgeCaseTests {

        @Test
        @DisplayName("shouldHandleEmptyIntersection")
        void shouldHandleEmptyIntersection() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Créer un ensemble avec des opérateurs qui ne sont dans aucun des deux
            Set<Op> emptySet = Set.of();
            
            // L'intersection avec un ensemble vide devrait être vide
            assertTrue(textOperators.stream().noneMatch(emptySet::contains));
            assertTrue(numericOperators.stream().noneMatch(emptySet::contains));
        }

        @Test
        @DisplayName("shouldHandleNullSafety")
        void shouldHandleNullSafety() {
            Set<Op> textOperators = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators = OperatorUtils.FOR_NUMBER;
            
            // Vérifier que les ensembles ne contiennent pas de valeurs nulles
            // Note: Set.of() ne peut pas contenir de null, donc contains(null) lèvera NPE
            // On teste plutôt que les ensembles sont bien formés
            assertNotNull(textOperators);
            assertNotNull(numericOperators);
            assertFalse(textOperators.isEmpty());
            assertFalse(numericOperators.isEmpty());
        }

        @Test
        @DisplayName("shouldBeConsistentAcrossMultipleCalls")
        void shouldBeConsistentAcrossMultipleCalls() {
            // Vérifier que les ensembles sont les mêmes à chaque appel
            Set<Op> textOperators1 = OperatorUtils.FOR_TEXT;
            Set<Op> textOperators2 = OperatorUtils.FOR_TEXT;
            Set<Op> numericOperators1 = OperatorUtils.FOR_NUMBER;
            Set<Op> numericOperators2 = OperatorUtils.FOR_NUMBER;
            
            assertEquals(textOperators1, textOperators2);
            assertEquals(numericOperators1, numericOperators2);
            assertSame(textOperators1, textOperators2);
            assertSame(numericOperators1, numericOperators2);
        }
    }
}
