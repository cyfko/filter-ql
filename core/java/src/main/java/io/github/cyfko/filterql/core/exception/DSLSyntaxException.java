package io.github.cyfko.filterql.core.exception;

import io.github.cyfko.filterql.core.api.DslParser;
import io.github.cyfko.filterql.core.api.FilterTree;
import io.github.cyfko.filterql.core.impl.BasicDslParser;

/**
 * Exception thrown when a DSL expression contains syntax errors or invalid references.
 * <p>
 * This exception is specifically designed to handle Domain Specific Language (DSL) parsing
 * errors in FilterQL expressions. It provides detailed error information to help developers
 * identify and fix syntax issues in their filter expressions.
 * </p>
 * 
 * <p><strong>Common Scenarios:</strong></p>
 * <ul>
 *   <li><strong>Invalid Syntax:</strong> Malformed boolean expressions</li>
 *   <li><strong>Unmatched Parentheses:</strong> Missing opening or closing parentheses</li>
 *   <li><strong>Invalid Identifiers:</strong> Filter names that don't match naming rules</li>
 *   <li><strong>Missing Operands:</strong> Operators without required operands</li>
 *   <li><strong>Unknown References:</strong> Filter keys not found in context</li>
 * </ul>
 * 
 * <p><strong>Error Examples and Messages:</strong></p>
 * <pre>{@code
 * // Invalid syntax examples that throw DSLSyntaxException:
 * 
 * // 1. Empty expression
 * parser.parse("");
 * // → "DSL expression cannot be null or empty"
 * 
 * // 2. Unmatched parentheses
 * parser.parse("(filter1 & filter2");
 * // → "Mismatched parentheses: unmatched '(' at position 0"
 * 
 * // 3. Invalid identifiers
 * parser.parse("123invalid");
 * // → "Invalid identifier '123invalid' at position 0. Identifiers must start with a letter..."
 * 
 * // 4. Missing operands
 * parser.parse("filter1 &");
 * // → "Binary operator '&' requires a left operand at position 8"
 * 
 * // 5. Unknown filter reference
 * filterTree.generate(context); // where context doesn't contain referenced filter
 * // → "Filter <unknownFilter> referenced in the combination expression does not exist."
 * }</pre>
 * 
 * <p><strong>Best Practices for Handling:</strong></p>
 * <pre>{@code
 * try {
 *     FilterTree tree = parser.parse(userExpression);
 *     Condition condition = tree.generate(context);
 * } catch (DSLSyntaxException e) {
 *     // Log the detailed error for debugging
 *     logger.error("DSL syntax error in expression '{}': {}", userExpression, e.getMessage());
 *     
 *     // Return user-friendly error response
 *     return ResponseEntity.badRequest()
 *         .body("Invalid filter expression: " + e.getMessage());
 * }
 * }</pre>
 * 
 * <p><strong>Error Recovery Strategies:</strong></p>
 * <ul>
 *   <li>Validate expressions before parsing in user interfaces</li>
 *   <li>Provide syntax highlighting and real-time validation</li>
 *   <li>Offer suggested corrections for common mistakes</li>
 *   <li>Log detailed error information for debugging</li>
 * </ul>
 * 
 * <p><strong>Integration with Development Tools:</strong></p>
 * <p>The detailed error messages include position information where possible,
 * making it easier to integrate with IDE plugins, syntax highlighters, and
 * interactive query builders that can highlight the exact location of errors.</p>
 *
 * @author Frank KOSSI
 * @since 2.0.0
 * @see DslParser
 * @see FilterTree
 * @see BasicDslParser
 */
public class DSLSyntaxException extends RuntimeException {

    /**
     * Constructor with an explanatory error message.
     * <p>
     * Creates a new DSLSyntaxException with a descriptive message explaining
     * the nature of the syntax error. The message should be user-friendly
     * and include position information when available.
     * </p>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * if (expression.isEmpty()) {
     *     throw new DSLSyntaxException("DSL expression cannot be null or empty");
     * }
     * 
     * if (hasUnmatchedParentheses) {
     *     throw new DSLSyntaxException("Mismatched parentheses: unmatched ')' at position " + position);
     * }
     * }</pre>
     *
     * @param message the message describing the cause of the exception, should be descriptive and include context
     */
    public DSLSyntaxException(String message) {
        super(message);
    }

    /**
     * Constructor with an explanatory message and an underlying cause.
     * <p>
     * Creates a new DSLSyntaxException that wraps another exception. This is useful
     * when a DSL parsing error is caused by an underlying system issue (like reflection
     * errors or I/O problems during expression processing).
     * </p>
     * 
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * try {
     *     // Attempt to parse complex expression involving reflection
     *     parseComplexExpression(expression);
     * } catch (ReflectiveOperationException e) {
     *     throw new DSLSyntaxException(
     *         "Failed to resolve field reference in expression: " + expression, e);
     * }
     * }</pre>
     *
     * @param message the message describing the cause of the exception
     * @param cause   the original cause of this exception (e.g., reflection errors, I/O errors)
     */
    public DSLSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}

