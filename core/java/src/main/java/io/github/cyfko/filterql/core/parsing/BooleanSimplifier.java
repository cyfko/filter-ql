package io.github.cyfko.filterql.core.parsing;

import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.config.DslReservedSymbol;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A utility class that simplifies boolean expressions by converting infix notation to postfix (Reverse Polish Notation)
 * using the Shunting-Yard algorithm and then applying boolean algebra simplifications during postfix evaluation.
 * <p>
 * The simplifier produces canonical, simplified boolean expressions by applying core boolean identities and laws such as:
 * </p>
 * <ul>
 *   <li>Idempotence: A & A → A, A | A → A</li>
 *   <li>Cancellation: A & !A → ⊥ (false), A | !A → ⊤ (true)</li>
 *   <li>Identity: A & ⊤ → A, A | ⊥ → A</li>
 *   <li>Annihilation: A & ⊥ → ⊥, A | ⊤ → ⊤</li>
 *   <li>Double negation: !!A → A</li>
 *   <li>De Morgan's laws: !(A & B) → !A | !B, !(A | B) → !A & !B</li>
 * </ul>
 *
 * <p>This class is designed to be used statically and cannot be instantiated.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * String expr = "(A & !A) | (B & ⊤)";
 * String simplified = BooleanSimplifier.simplify(expr);
 * // Result: "⊤ B & |"
 * }</pre>
 *
 * <h2>Example 2: Postfix Validation</h2>
 * <p>
 * The simplifier can now validate postfix expressions with fail-fast error detection:
 * </p>
 * <pre>{@code
 * List<String> postfix = FastPostfixConverter.toPostfix("(a & b) | c", 1000);
 * List<String> validated = BooleanSimplifier.validateAndSimplifyPostfix(
 *     postfix,
 *     100,  // maxTokens
 *     true  // validateIdentifiers
 * );
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public final class BooleanSimplifier {

    private static final int MAX_ITERATIONS = 100;

    private BooleanSimplifier() {}

    /**
     * Simplifies the given boolean expression string by parsing it, converting to postfix notation,
     * and applying boolean simplification rules.
     * <p>
     * This call is equivalent to:
     * </p>
     * <pre>{@code
     * DslPolicy dslPolicy = DslPolicy.defaults();
     *
     * List<String> postfix = FastPostfixConverter.toPostfix(expr,dslPolicy);
     *
     * return BooleanSimplifier.validateAndSimplifyPostfix(postfix, dslPolicy)
     *                         .stream()
     *                         .collect(Collectors.joining(" "));
     * }</pre>
     *
     * @param expr the boolean expression in infix notation, null or blank returns as is
     * @return the simplified boolean expression in postfix notation with tokens separated by spaces
     */
    public static String simplify(String expr) {
        DslPolicy dslPolicy = DslPolicy.defaults();
        List<String> postfix = FastPostfixConverter.toPostfix(expr, dslPolicy);
        return BooleanSimplifier.validateAndSimplifyPostfix(postfix, dslPolicy)
                .stream().collect(Collectors.joining(" "));
    }


    /**
     * Simplifies the given boolean expression string by parsing it, converting to postfix notation,
     * and applying boolean simplification rules.
     * <p>
     * This call is equivalent to:
     * </p>
     * <pre>{@code
     * DslPolicy dslPolicy = DslPolicy.defaults();
     *
     * List<String> postfix = FastPostfixConverter.toPostfix(expr,dslPolicy);
     *
     * return BooleanSimplifier.validateAndSimplifyPostfix(postfix, dslPolicy)
     *                         .stream()
     *                         .collect(Collectors.joining(" "));
     * }</pre>
     *
     * @param expr the boolean expression in infix notation, null or blank returns as is
     * @return the simplified boolean expression in postfix notation with tokens separated by spaces
     */
    public static String simplify(String expr, DslPolicy dslPolicy) {
        Objects.requireNonNull(dslPolicy, "DSL policy is required");
        List<String> postfix = FastPostfixConverter.toPostfix(expr, dslPolicy);
        return BooleanSimplifier.validateAndSimplifyPostfix(postfix, dslPolicy)
                .stream().collect(Collectors.joining(" "));
    }

    /**
     * Validates and simplifies postfix expression with fail-fast error detection.
     * <p>
     * This is the <strong>recommended entry point</strong> for production use, as it:
     * </p>
     * <ul>
     *   <li>Validates identifier syntax (if enabled)</li>
     *   <li>Enforces token count size (DoS protection)</li>
     *   <li>Detects malformed postfix during simplification</li>
     *   <li>Returns simplified postfix for optimal cache efficiency</li>
     * </ul>
     *
     * @param postfixTokens postfix token list (from {@link FastPostfixConverter})
     * @param dslPolicy DSL syntax policy (DoS protection)
     * @return simplified postfix token list
     * @throws DSLSyntaxException if validation fails
     * @since 4.0.1
     */
    public static List<String> validateAndSimplifyPostfix(List<String> postfixTokens, DslPolicy dslPolicy) {

        if (postfixTokens == null || postfixTokens.isEmpty()) {
            throw new DSLSyntaxException("Postfix tokens cannot be null or empty");
        }

        // Validate identifiers (fail-fast)
        if (dslPolicy.validateIdentifiers()) {
            for (String token : postfixTokens) {
                if (!isOperator(token) && ! dslPolicy.identifierPattern().matcher(token).matches()) {
                    throw new DSLSyntaxException(String.format(
                        "Invalid identifier '%s'. " +
                        "Identifiers must start with a letter or underscore and contain only alphanumeric characters and underscores.",
                        token
                    ));
                }
            }
        }

        // Simplify with fail-fast error detection
        return evaluatePostfixSimplify(postfixTokens);
    }

    /**
     * Checks if token is an operator.
     */
    private static boolean isOperator(String token) {
        return "&".equals(token) || "|".equals(token) || "!".equals(token);
    }

    /**
     * Evaluates the tokens in postfix notation and applies boolean simplification rules repeatedly
     * until no further simplification can be made.
     *
     * The evaluation applies transformations including double negation elimination and binary operator simplifications.
     * After simplification, converts all prefix negations ("!A") into postfix notation ("A !").
     *
     * @param tokens a collection of boolean expression tokens in postfix notation
     * @return a list of tokens representing the simplified postfix expression
     * @throws DSLSyntaxException if simplification exceeds MAX_ITERATIONS (DoS protection)
     */
    private static List<String> evaluatePostfixSimplify(Collection<String> tokens) throws NoSuchElementException {
        boolean changed;
        List<String> current = new ArrayList<>(tokens);
        int iterations = 0;

        do {
            // Fail-fast: prevent infinite simplification loops (DoS protection)
            if (++iterations > MAX_ITERATIONS) {
                throw new DSLSyntaxException(String.format(
                    "Simplification exceeded maximum iterations (%d). " +
                    "Expression may be malformed or too complex.",
                    MAX_ITERATIONS
                ));
            }

            Deque<String> stack = new ArrayDeque<>();
            int beforeSize = current.size();

            for (String token : current) {
                switch (token) {
                    case "!" -> {
                        String operand = stack.pop();
                        if (operand.startsWith("!")) {
                            // Simplify double negation: !!A → A
                            stack.push(operand.substring(1));
                        } else {
                            stack.push("!" + operand);
                        }
                    }
                    case "&", "|" -> {
                        String b = stack.pop();
                        String a = stack.pop();
                        stack.push(simplifyBinary(a, b, token));
                    }
                    default -> stack.push(token);
                }
            }

            // Flatten the stack to a token list, splitting concatenated tokens if needed
            List<String> flattened = new ArrayList<>(stack.size() * 2);
            for (String s : stack) {
                if (s.indexOf(' ') >= 0) {
                    Collections.addAll(flattened, s.trim().split("\\s+"));
                } else {
                    flattened.add(s);
                }
            }

            changed = flattened.size() < beforeSize;
            current = flattened;
        } while (changed);

        // Convert prefix negations (!A) into postfix notation (A !)
        List<String> result = new ArrayList<>(current.size());
        for (String s : current) {
            if (s.startsWith("!")) {
                result.add(s.substring(1));
                result.add("!");
            } else {
                result.add(s);
            }
        }

        return result;
    }

    /**
     * Performs boolean simplification on a binary expression given operands and an operator.
     * Applies boolean algebra rules such as identity, annihilation, cancellation, and idempotence.
     *
     * @param a  the first operand (already simplified)
     * @param b  the second operand (already simplified)
     * @param op the boolean operator ("&" or "|")
     * @return the simplified expression string in postfix notation or a combined token string if no simplification applies
     */
    private static String simplifyBinary(String a, String b, String op) {
        // Identity and annihilation simplifications for AND
        if (op.equals("&")) {
            if (a.equals(DslReservedSymbol.TRUE)) return b;      // A & ⊤ → A
            if (b.equals(DslReservedSymbol.TRUE)) return a;      // ⊤ & A → A
            if (a.equals(DslReservedSymbol.FALSE) || b.equals(DslReservedSymbol.FALSE)) return DslReservedSymbol.FALSE; // A & ⊥ → ⊥
        }

        // Identity and annihilation simplifications for OR
        if (op.equals("|")) {
            if (a.equals(DslReservedSymbol.FALSE)) return b;     // A | ⊥ → A
            if (b.equals(DslReservedSymbol.FALSE)) return a;     // ⊥ | A → A
            if (a.equals(DslReservedSymbol.TRUE) || b.equals(DslReservedSymbol.TRUE)) return DslReservedSymbol.TRUE;    // A | ⊤ → ⊤
        }

        // Cancellation: A & !A → ⊥, A | !A → ⊤
        if (a.equals("!" + b) || b.equals("!" + a)) return op.equals("&") ? DslReservedSymbol.FALSE : DslReservedSymbol.TRUE;

        // Idempotence: A & A = A, A | A = A
        if (a.equals(b)) return a;

        // No simplification possible, return combined postfix expression
        return a + " " + b + " " + op;
    }
}