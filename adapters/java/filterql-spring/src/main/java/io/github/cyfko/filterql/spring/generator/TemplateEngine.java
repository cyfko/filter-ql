package io.github.cyfko.filterql.spring.generator;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Simple template engine for generating Java source code.
 * <p>
 * This engine replaces placeholders of the form <code>${key}</code> in template files
 * with corresponding values provided in a context map.
 * </p>
 * <p>
 * Typical usage involves loading a template from resources and processing it with a context map
 * to produce the final source code or configuration.
 * </p>
 *
 * <b>Example usage:</b>
 * <pre>{@code
 * TemplateEngine engine = new TemplateEngine();
 * String template = engine.loadTemplate("my-template.java.tpl");
 * Map<String, Object> context = new HashMap<>();
 * context.put("className", "MyClass");
 * String result = engine.process(template, context);
 * }</pre>
 */
public class TemplateEngine {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Loads a template file from the resources directory.
     *
     * @param templateName the name of the template file to load (relative to /templates/)
     * @return the template content as a String
     * @throws IOException if the template cannot be read
     */
    public String loadTemplate(String templateName) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/" + templateName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Processes a template by replacing all placeholders with values from the context map.
     * <p>
     * Placeholders are of the form <code>${key}</code> and are replaced by the value associated with <code>key</code>.
     * </p>
     *
     * @param template the template string containing placeholders
     * @param context a map of keys to values for placeholder replacement
     * @return the processed template with all placeholders replaced
     * @throws IllegalArgumentException if a placeholder is missing in the context map
     */
    public String process(String template, Map<String, Object> context) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = context.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Missing template variable: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
