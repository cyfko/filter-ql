package io.github.cyfko.filterql.core.parsing;

import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;

import java.util.*;

/**
 * Ultra-fast DSL to postfix converter with minimal validation.
 * <p>
 * This class focuses on <strong>speed over safety</strong>, performing only essential DoS protection:
 * </p>
 * <ul>
 *   <li>Expression length size (prevents memory exhaustion)</li>
 *   <li>Basic parentheses balancing (prevents stack overflow)</li>
 *   <li>No identifier validation (deferred to simplification phase)</li>
 *   <li>No syntax transition validation (deferred to simplification phase)</li>
 * </ul>
 *
 * <p><strong>Design philosophy:</strong></p>
 * <pre>
 * Phase 1 (this class): Fast postfix conversion + DoS protection only
 * Phase 2 (BooleanSimplifier): Simplification + full validation
 * </pre>
 *
 * <p><strong>Performance characteristics:</strong></p>
 * <ul>
 *   <li>Time: O(n) where n = expression length</li>
 *   <li>Space: O(n) for output list + O(log n) for operator stack (typically)</li>
 *   <li>No regex matching</li>
 *   <li>No object allocation except for output list</li>
 *   <li>Single pass tokenization + conversion</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * int maxLength = 1000;
 *
 * try {
 *     List<String> postfix = FastPostfixConverter.toPostfix(
 *         "(filter1 & filter2) | !filter3",
 *         maxLength
 *     );
 *     // Result: ["filter1", "filter2", "&", "filter3", "!", "|"]
 *
 *     // Pass to simplifier for validation + optimization
 *     List<String> simplified = BooleanSimplifier.simplifyPostfix(postfix);
 *
 * } catch (DSLSyntaxException e) {
 *     // Only DoS-related errors thrown here
 * }
 * }</pre>
 *
 * @author Frank KOSSI
 * @since 4.0.1
 */
public final class FastPostfixConverter {

    private static final Map<String, Integer> PRECEDENCE = Map.of("!", 3, "&", 2, "|", 1);

    private FastPostfixConverter() {}

    public static List<String> toPostfix(String expression, DslPolicy dslPolicy) {
        if (expression == null || expression.isBlank()) {
            throw new DSLSyntaxException("DSL expression cannot be null or empty");
        }

        String trimmed = expression.trim();

        // DoS protection
        if (trimmed.length() > dslPolicy.maxExpressionLength()) {
            throw new DSLSyntaxException(String.format(
                    "Expression too long (%d characters, max: %d). Policy applied: %s",
                    trimmed.length(), dslPolicy.maxExpressionLength(), dslPolicy.policyName()
            ));
        }

        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);

        if (first == '&' || first == '|' || last == '&' || last == '|' || last == '!') {
            throw new DSLSyntaxException(String.format(
                    "Malformed expression: %s",
                    (first == '&' || first == '|') ? "starts with '" + first + "'" : "ends with '" + last + "'"
            ));
        }

        return convertToPostfix(trimmed, dslPolicy);
    }

    private static List<String> convertToPostfix(String expression, DslPolicy dslPolicy) {

        List<String> output = new ArrayList<>(expression.length());
        Deque<String> operators = new ArrayDeque<>(expression.length());
        StringBuilder currentToken = new StringBuilder();

        // Preserve insertion order for deterministic simplification and output
        boolean onlyOrExpression = true;
        boolean onlyAndExpression = true;
        int repeatedTokenCount = 0;
        Map<String, Boolean> tokenMultiplicity = new LinkedHashMap<>(expression.length());

        // RPN processing
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (Character.isWhitespace(c)) {
                repeatedTokenCount += flushToken(currentToken, output, tokenMultiplicity);
                continue;
            }

            if (isOperatorChar(c)) {
                repeatedTokenCount += flushToken(currentToken, output, tokenMultiplicity);

                String op = String.valueOf(c);
                switch (c) {
                    case '(' -> operators.push(op);

                    case ')' -> {
                        while (!operators.isEmpty() && !operators.peek().equals("(")) {
                            output.add(operators.pop());
                        }
                        if (operators.isEmpty()) {
                            throw new DSLSyntaxException("Mismatched parentheses: unmatched ')'");
                        }
                        operators.pop();
                    }

                    case '!' -> {
                        // Simplifie les paires de '!!' directement pendant le parsing
                        if (!operators.isEmpty() && operators.peek().equals("!")) {
                            operators.pop(); // !! -> supprime les deux
                        } else {
                            operators.push(op);
                        }
                        onlyOrExpression = onlyAndExpression = false;
                    }

                    case '&', '|' -> {
                        while (!operators.isEmpty() && isHigherOrEqualPrecedence(operators.peek(), op)) {
                            output.add(operators.pop());
                        }
                        operators.push(op);
                        onlyOrExpression &= (c == '|');
                        onlyAndExpression &= (c == '&');
                    }
                }
            } else {
                currentToken.append(c);
            }
        }

        repeatedTokenCount += flushToken(currentToken, output, tokenMultiplicity);

        while (!operators.isEmpty()) {
            String op = operators.pop();
            if (op.equals("(")) {
                throw new DSLSyntaxException("Mismatched parentheses: unmatched '('");
            }
            output.add(op);
        }

        boolean reductionRatio = !tokenMultiplicity.isEmpty() &&
                ((double) repeatedTokenCount / tokenMultiplicity.size() > 0.25);

        if (onlyOrExpression && reductionRatio) {
            return optimizeHomogeneousOpsRecursive("|", tokenMultiplicity);
        } else if (onlyAndExpression && reductionRatio) {
            return optimizeHomogeneousOpsRecursive("&", tokenMultiplicity);
        }

        return output;
    }

    private static int flushToken(StringBuilder currentToken, List<String> output, Map<String, Boolean> multiplicity) {
        if (currentToken.isEmpty()) return 0;
        String token = currentToken.toString();
        output.add(token);
        currentToken.setLength(0);

        boolean repeated = multiplicity.containsKey(token);
        multiplicity.put(token, true);
        return repeated ? 1 : 0;
    }

    private static boolean isOperatorChar(char c) {
        return c == '&' || c == '|' || c == '!' || c == '(' || c == ')';
    }

    private static boolean isHigherOrEqualPrecedence(String stackOp, String token) {
        if ("(".equals(stackOp)) return false;
        return PRECEDENCE.getOrDefault(stackOp, 0) >= PRECEDENCE.getOrDefault(token, 0);
    }

    private static List<String> optimizeHomogeneousOpsRecursive(String op, Map<String, Boolean> multiplicity) {
        List<String> uniqueTokens = new ArrayList<>(multiplicity.size());
        Set<String> seen = new HashSet<>(multiplicity.size());

        for (String token : multiplicity.keySet()) {
            if (seen.add(token)) uniqueTokens.add(token);
        }

        int opCount = uniqueTokens.size() - 1;
        for (int i = 0; i < opCount; i++) uniqueTokens.add(op);

        return uniqueTokens;
    }
}
