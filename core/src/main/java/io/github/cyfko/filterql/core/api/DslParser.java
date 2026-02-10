package io.github.cyfko.filterql.core.api;

import io.github.cyfko.filterql.core.exception.DSLSyntaxException;

/**
 * Framework-agnostic parser for transforming boolean DSL expressions into {@link FilterTree} structures.
 * <p>
 * This interface defines the core parsing contract for FilterQL's Domain-Specific Language (DSL).
 * The parser transforms textual filter expressions into abstract syntax trees that can be
 * evaluated against backend-specific contexts.
 * </p>
 *
 * <h2>DSL Grammar</h2>
 * <p>
 * The DSL supports boolean logic composition using three operators and parentheses:
 * </p>
 * <table border="1">
 * <caption>DSL Operator Reference</caption>
 * <thead>
 * <tr><th>Operator</th><th>Symbol</th><th>Precedence</th><th>Associativity</th><th>Example</th></tr>
 * </thead>
 * <tbody>
 * <tr><td>Parentheses</td><td>( )</td><td>Highest</td><td>N/A</td><td>(filter1 &amp; filter2)</td></tr>
 * <tr><td>NOT</td><td>!</td><td>3</td><td>Right</td><td>!filter1</td></tr>
 * <tr><td>AND</td><td>&amp;</td><td>2</td><td>Left</td><td>filter1 &amp; filter2</td></tr>
 * <tr><td>OR</td><td>|</td><td>1</td><td>Left</td><td>filter1 | filter2</td></tr>
 * </tbody>
 * </table>
 *
 * <h2>Grammar Specification (EBNF)</h2>
 * <pre>
 * expression := term (OR term)*
 * term       := factor (AND factor)*
 * factor     := NOT? (identifier | '(' expression ')')
 * identifier := [a-zA-Z_][a-zA-Z0-9_]*
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <h3>Simple Expressions</h3>
 * <pre>{@code
 * DslParser parser = // implementation-specific;
 *
 * // Single filter reference
 * FilterTree tree1 = parser.parse("active");
 *
 * // Binary operations
 * FilterTree tree2 = parser.parse("active & premium");         // AND
 * FilterTree tree3 = parser.parse("active | premium");         // OR
 * FilterTree tree4 = parser.parse("!deleted");                 // NOT
 * }</pre>
 *
 * <h3>Complex Expressions</h3>
 * <pre>{@code
 * // Precedence control with parentheses
 * FilterTree tree5 = parser.parse("(active & premium) | vip");
 *
 * // Mixed operators
 * FilterTree tree6 = parser.parse("!deleted & (active | pending)");
 *
 * // Nested negations
 * FilterTree tree7 = parser.parse("!!active");  // Double negation
 *
 * // Deeply nested
 * FilterTree tree8 = parser.parse("((a & b) | (c & d)) & !(e | f)");
 * }</pre>
 *
 * <h2>Whitespace Handling</h2>
 * <p>
 * Whitespace is ignored during parsing and can be used freely for readability:
 * </p>
 * <pre>{@code
 * parser.parse("a&b");           // Compact
 * parser.parse("a & b");         // Readable
 * parser.parse("  a  &  b  ");   // Extra whitespace (ignored)
 * }</pre>
 *
 * <h2>Error Detection</h2>
 * <p>
 * The parser performs comprehensive validation and reports specific errors:
 * </p>
 * <ul>
 *   <li><strong>Syntax Errors:</strong> Malformed expressions, invalid characters</li>
 *   <li><strong>Missing Operands:</strong> Incomplete binary operations</li>
 *   <li><strong>Unmatched Parentheses:</strong> Mismatched ( or )</li>
 *   <li><strong>Empty Expressions:</strong> Null or blank input</li>
 * </ul>
 *
 * <h3>Invalid Expression Examples</h3>
 * <pre>{@code
 * parser.parse("");              // DSLSyntaxException: Empty expression
 * parser.parse("filter &");      // DSLSyntaxException: Missing operand
 * parser.parse("& filter");      // DSLSyntaxException: Missing operand
 * parser.parse("(filter");       // DSLSyntaxException: Unmatched parenthesis
 * parser.parse("filter & & x");  // DSLSyntaxException: Double operator
 * }</pre>
 *
 * <h2>Implementation Requirements</h2>
 * <ul>
 *   <li>Must support all standard boolean operators and parentheses</li>
 *   <li>Must respect operator precedence and associativity</li>
 *   <li>Must produce deterministic results for identical input</li>
 *   <li>Should optimize for O(n) or better time complexity</li>
 *   <li>Should provide clear, actionable error messages</li>
 * </ul>
 *
 * @see FilterTree
 * @see DSLSyntaxException
 * @author Frank KOSSI
 * @since 1.0
 */
public interface DslParser {
    
    /**
     * Parses a DSL expression string into a FilterTree.
     * <p>
     * Transforms a textual filter expression into an abstract syntax tree
     * that preserves the logical structure and operator precedence.
     * The resulting tree can be evaluated multiple times against different contexts.
     * </p>
     * 
     * <p><strong>Valid Expression Examples:</strong></p>
     * <pre>{@code
     * // Simple cases
     * parser.parse("userFilter");                    // Single filter reference
     * parser.parse("name & age");                    // AND combination
     * parser.parse("active | premium");              // OR combination
     * parser.parse("!deleted");                      // NOT operation
     * 
     * // Complex cases
     * parser.parse("(name & age) | status");         // Precedence control
     * parser.parse("!deleted & (active | pending)"); // Nested logic
     * parser.parse("a & b | c & d");                 // Multiple operators
     * }</pre>
     * 
     * <p><strong>Invalid Expression Examples:</strong></p>
     * <pre>{@code
     * parser.parse("");                              // Empty expression
     * parser.parse("filter &");                      // Missing operand
     * parser.parse("& filter");                      // Missing operand
     * parser.parse("(filter");                       // Unmatched parenthesis
     * parser.parse("filter & & other");              // Double operator
     * }</pre>
     * 
     * @param dslExpression The DSL expression to parse (e.g., "(f1 &amp; f2) | !f3").
     *                     Must not be null or empty. Whitespace is ignored.
     * @return A FilterTree representing the parsed expression structure.
     *         Filter definitions are provided separately to FilterTree.prepare()
     * @throws DSLSyntaxException if the DSL expression has invalid syntax,
     *                           contains unknown operators, or has structural errors
     * @throws NullPointerException if dslExpression is null
     */
    FilterTree parse(String dslExpression) throws DSLSyntaxException;
}
