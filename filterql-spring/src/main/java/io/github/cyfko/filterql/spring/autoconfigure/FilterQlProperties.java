package io.github.cyfko.filterql.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

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
