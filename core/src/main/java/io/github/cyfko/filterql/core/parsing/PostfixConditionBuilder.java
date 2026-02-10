package io.github.cyfko.filterql.core.parsing;

import io.github.cyfko.filterql.core.api.Condition;
import io.github.cyfko.filterql.core.api.FilterContext;
import io.github.cyfko.filterql.core.config.DslPolicy;
import io.github.cyfko.filterql.core.exception.DSLSyntaxException;
import io.github.cyfko.filterql.core.model.FilterDefinition;
import io.github.cyfko.filterql.core.api.PropertyReference;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link Condition} from a simplified postfix expression in a single pass.
 * <p>
 * This builder is designed for optimal performance by traversing the postfix token list
 * exactly once and constructing the condition tree on-the-fly using a stack-based algorithm.
 * </p>
 *
 * <h2>Architecture</h2>
 * <p>
 * The builder integrates with the two-phase parsing system:
 * </p>
 * <ol>
 *   <li><strong>Phase 1</strong>: {@link FastPostfixConverter} - Fast postfix conversion (DoS protection only)</li>
 *   <li><strong>Phase 2</strong>: {@link BooleanSimplifier} - Validation and simplification</li>
 *   <li><strong>Phase 3</strong>: {@link PostfixConditionBuilder} - Single-pass Condition construction</li>
 * </ol>
 *
 * <h2>Algorithm</h2>
 * <p>
 * Uses classic postfix evaluation with a stack:
 * </p>
 * <pre>
 * For each token in postfix expression:
 *   - If IDENTIFIER: resolve to Condition via context, push to stack
 *   - If NOT (!): pop operand, apply .not(), push result
 *   - If AND (&): pop right, pop left, apply left.and(right), push result
 *   - If OR (|): pop right, pop left, apply left.or(right), push result
 *
 * Stack should contain exactly ONE condition at the end.
 * </pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li><strong>Time Complexity</strong>: O(n) where n = number of tokens</li>
 *   <li><strong>Space Complexity</strong>: O(d) where d = max nesting depth</li>
 *   <li><strong>Allocations</strong>: Minimal - single stack + condition objects</li>
 *   <li><strong>Passes</strong>: Single pass over postfix tokens</li>
 * </ul>
 *
 * <h2>Error Detection</h2>
 * <p>
 * Performs fail-fast validation during construction:
 * </p>
 * <ul>
 *   <li>Stack underflow (operator without enough operands)</li>
 *   <li>Stack overflow (multiple conditions left on stack at end)</li>
 *   <li>Undefined filter references (via Context)</li>
 *   <li>Invalid filter combinations (via Condition operations)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Phase 1: Fast postfix conversion
 * List<String> postfix = FastPostfixConverter.toPostfix("(a & b) | c", 1000);
 *
 * // Phase 2: Validation and simplification
 * List<String> simplified = BooleanSimplifier.validateAndSimplifyPostfix(postfix, 100, true);
 *
 * // Phase 3: Single-pass Condition construction
 * Context context = prepareContext(); // contains "a", "b", "c" filter definitions
 * Condition result = PostfixConditionBuilder.build(simplified, context);
 *
 * // Result represents: (a AND b) OR c
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is stateless and thread-safe. All methods are static and reentrant.
 * </p>
 *
 * @author Frank KOSSI
 * @since 4.0.1
 * @see FastPostfixConverter
 * @see BooleanSimplifier
 * @see Condition
 * @see FilterContext
 */
public final class PostfixConditionBuilder {

    private PostfixConditionBuilder() {
        // Utility class - prevent instantiation
    }

    /**
     * Builds a {@link Condition} from a simplified postfix token list in a single pass.
     *
     * <p>
     * <strong>Important</strong>: The postfix tokens must be already simplified via
     * {@link BooleanSimplifier#validateAndSimplifyPostfix(List, DslPolicy)}.
     * This method does NOT perform validation or simplification - it assumes the input
     * is clean and well-formed.
     * </p>
     *
     * <p>
     * <strong>Algorithm</strong>: Classic postfix evaluation using a stack. For each token:
     * </p>
     * <ul>
     *   <li>NOT (!): pop, apply {@link Condition#not()}, push result</li>
     *   <li>AND (&): pop right & left, apply {@link Condition#and(Condition)}, push result</li>
     *   <li>OR (|): pop right & left, apply {@link Condition#or(Condition)}, push result</li>
     * </ul>
     *
     * @param postfixTokens simplified postfix token list
     * @param definitions a mapping between each filter identifier within the {@code postfixToken} to its {@link FilterDefinition}
     * @param filterContext context providing filter-to-condition mapping
     * @return the root condition representing the entire expression
     * @throws DSLSyntaxException if the expression is malformed (stack errors) or references undefined filters
     * @throws NullPointerException if postfixTokens or context is null
     */
    public static <P extends Enum<P> & PropertyReference> Condition build(List<String> postfixTokens, Map<String, FilterDefinition<P>> definitions, FilterContext filterContext) {
        if (postfixTokens == null) {
            throw new NullPointerException("postfixTokens cannot be null");
        }
        if (filterContext == null) {
            throw new NullPointerException("context cannot be null");
        }

        if (postfixTokens.isEmpty()) {
            throw new DSLSyntaxException("Cannot build condition from empty postfix expression");
        }

        Deque<Condition> stack = new ArrayDeque<>();

        for (String token : postfixTokens) {
            switch (token) {
                case "!" -> {
                    // Unary NOT: requires 1 operand
                    if (stack.isEmpty()) {
                        throw new DSLSyntaxException(
                            "Malformed postfix expression: NOT operator (!) without operand. " +
                            "Expression may be incomplete or improperly simplified."
                        );
                    }
                    Condition operand = stack.pop();
                    stack.push(operand.not());
                }

                case "&" -> {
                    // Binary AND: requires 2 operands
                    if (stack.size() < 2) {
                        throw new DSLSyntaxException(
                            "Malformed postfix expression: AND operator (&) requires two operands. " +
                            "Stack contains only " + stack.size() + " condition(s)."
                        );
                    }
                    Condition right = stack.pop();
                    Condition left = stack.pop();
                    stack.push(left == right ? left : left.and(right));
                }

                case "|" -> {
                    // Binary OR: requires 2 operands
                    if (stack.size() < 2) {
                        throw new DSLSyntaxException(
                            "Malformed postfix expression: OR operator (|) requires two operands. " +
                            "Stack contains only " + stack.size() + " condition(s)."
                        );
                    }
                    Condition right = stack.pop();
                    Condition left = stack.pop();
                    stack.push(left == right ? left : left.or(right));
                }

                default -> {
                    // Identifier: resolve to Condition via context
                    // This is where DSL identifier validation happens (deferred from FilterRequest constructor)
                    FilterDefinition<P> definition = definitions.get(token);
                    if (definition == null) {
                        throw new DSLSyntaxException(String.format(
                            "DSL expression references undefined filter '%s'. Available filters: %s",
                            token, definitions.keySet()
                        ));
                    }
                    
                    try {
                        Condition condition = filterContext.toCondition(token, definition.ref(), definition.op());
                        if (condition == null) {
                            throw new DSLSyntaxException(String.format(
                                "Context failed to create condition for filter '%s'",
                                token
                            ));
                        }
                        stack.push(condition);
                    } catch (NullPointerException | IllegalArgumentException e) {
                        throw new DSLSyntaxException(String.format(
                            "Failed to create condition for filter '%s': %s",
                            token, e.getMessage()
                        ), e);
                    }
                }
            }
        }

        // Final validation: stack must contain exactly ONE condition
        if (stack.isEmpty()) {
            throw new DSLSyntaxException(
                "Malformed postfix expression: evaluation resulted in empty stack. " +
                "Expression may contain only operators without operands."
            );
        }

        if (stack.size() > 1) {
            throw new DSLSyntaxException(String.format(
                "Malformed postfix expression: evaluation resulted in %d conditions on stack (expected 1). " +
                "Expression may have too many operands or missing operators.",
                stack.size()
            ));
        }

        return stack.pop();
    }
}
