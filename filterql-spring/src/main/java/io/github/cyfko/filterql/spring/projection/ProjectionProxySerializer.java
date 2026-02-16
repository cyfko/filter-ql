package io.github.cyfko.filterql.spring.projection;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson serializer for {@link ProjectionProxy} instances.
 *
 * <p>
 * Serializes only the fields that were actually projected (keys present
 * in the underlying Map). This ensures that:
 * </p>
 * <ul>
 * <li>Projected fields with {@code null} values appear as
 * {@code "field": null}</li>
 * <li>Non-projected fields are completely absent from the JSON output</li>
 * </ul>
 *
 * @author Frank KOSSI
 * @since 4.0.0
 */
public class ProjectionProxySerializer extends JsonSerializer<ProjectionProxy> {

    @Override
    public void serialize(ProjectionProxy proxy, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        Map<String, Object> data = proxy._getProjectedData();
        gen.writeStartObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            gen.writeObjectField(entry.getKey(), entry.getValue());
        }
        gen.writeEndObject();
    }
}
