package io.github.cyfko.filterql.spring.generator;

import io.github.cyfko.filterql.core.validation.Op;
import io.github.cyfko.filterql.spring.processor.model.FieldMetadata;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PropertyRefEnumGeneratorTest {

    @Test
    void shouldGenerateEnumWithCorrectSyntax() throws Exception {
        // Contract test: Generate enum using metadata directly (no runtime annotation needed)
        TemplateEngine engine = new TemplateEngine();
        PropertyRefEnumGenerator generator = new PropertyRefEnumGenerator(engine);
        List<FieldMetadata> fields = List.of(
                FieldMetadata.regularField("NAME", "name", new Op[]{ Op.EQ, Op.MATCHES }),
                FieldMetadata.regularField("AGE", "age", new Op[]{ Op.GT, Op.LT })
        );

        // Use contract testing version: pass i18nPrefix directly instead of annotation
        String generated = generator.generate(
                "com.example",
                "User",
                "UserPropertyRef",
                fields
        );

        // Vérifications plus robustes
        assertTrue(generated.contains("NAME, AGE;"));

        // Vérifie la présence des opérateurs sans dépendre de l'ordre
        assertTrue(generated.contains("NAME -> Set.of(Op.EQ, Op.MATCHES);"));
        assertTrue(generated.contains("AGE -> Set.of(Op.GT, Op.LT);"));

        // Vérifie la structure globale
        assertTrue(generated.contains("public enum UserPropertyRef implements PropertyReference"));
    }
}
