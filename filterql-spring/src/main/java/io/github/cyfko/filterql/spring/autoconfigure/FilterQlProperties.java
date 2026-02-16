package io.github.cyfko.filterql.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

/**
 * Spring Boot
 * {@link org.springframework.boot.context.properties.ConfigurationProperties}
 * bound to the {@code filterql.*} namespace in {@code application.yml} or
 * {@code application.properties}.
 * <p>
 * Configuration groups:
 * </p>
 * <ul>
 * <li>{@code filterql.auto-generation.enabled} (default: {@code true}) —
 * enables/disables automatic REST endpoint generation from {@code @Exposure}
 * annotations.</li>
 * <li>{@code filterql.auto-generation.exclude-by-default} (default:
 * {@code ["password","token","secret"]}) —
 * field names that are never exposed in generated endpoints.</li>
 * <li>{@code filterql.i18n.enabled} (default: {@code true}) — enables i18n
 * support for error messages.</li>
 * <li>{@code filterql.i18n.fallback-to-field-name} (default: {@code true}) —
 * when no i18n key is found,
 * uses the raw field name instead of throwing.</li>
 * </ul>
 *
 * @see FilterQlAutoConfiguration
 * @author Frank KOSSI
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = "filterql")
public class FilterQlProperties {
    private AutoGeneration autoGeneration = new AutoGeneration();
    private I18n i18n = new I18n();

    public static class AutoGeneration {
        private boolean enabled = true;
        private List<String> excludeByDefault = List.of("password", "token", "secret");
        // getters/setters
    }

    public static class I18n {
        private boolean enabled = true;
        private boolean fallbackToFieldName = true;
        // getters/setters
    }
    // getters/setters
}
