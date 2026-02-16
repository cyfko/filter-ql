package io.github.cyfko.filterql.spring.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Jackson serialization of projection proxies.
 */
class ProjectionProxySerializerTest {

    interface PersonProjection {
        String getFirstName();

        String getLastName();

        Integer getAge();

        String getEmail();

        Boolean isActive();
    }

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ProjectionJacksonModule());
    }

    @Test
    @DisplayName("should serialize only projected fields")
    void shouldSerializeOnlyProjectedFields() throws JsonProcessingException {
        // Only firstName and age are projected (2 out of 5 fields)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", "Frank");
        data.put("age", 30);

        PersonProjection proxy = ProjectionProxyFactory.create(PersonProjection.class, data);
        String json = objectMapper.writeValueAsString(proxy);

        assertTrue(json.contains("\"firstName\""));
        assertTrue(json.contains("\"Frank\""));
        assertTrue(json.contains("\"age\""));
        assertTrue(json.contains("30"));
        // Non-projected fields must be absent
        assertFalse(json.contains("\"lastName\""));
        assertFalse(json.contains("\"email\""));
        assertFalse(json.contains("\"active\""));
    }

    @Test
    @DisplayName("should serialize null-projected field as null in JSON")
    void shouldSerializeNullProjectedField() throws JsonProcessingException {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName", "Frank");
        data.put("lastName", null); // projected but null

        PersonProjection proxy = ProjectionProxyFactory.create(PersonProjection.class, data);
        String json = objectMapper.writeValueAsString(proxy);

        assertTrue(json.contains("\"firstName\":\"Frank\""));
        assertTrue(json.contains("\"lastName\":null"));
        // Non-projected fields still absent
        assertFalse(json.contains("\"age\""));
        assertFalse(json.contains("\"email\""));
    }

    @Test
    @DisplayName("should serialize empty projection as empty JSON object")
    void shouldSerializeEmptyProjection() throws JsonProcessingException {
        interface EmptyProjection {
        }

        EmptyProjection proxy = ProjectionProxyFactory.create(EmptyProjection.class, Map.of());
        String json = objectMapper.writeValueAsString(proxy);

        assertEquals("{}", json);
    }

    @Test
    @DisplayName("should serialize all fields when all are projected")
    void shouldSerializeAllFieldsWhenAllProjected() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", "Frank");
        data.put("lastName", "KOSSI");
        data.put("age", 30);
        data.put("email", "frank@example.com");
        data.put("active", true);

        PersonProjection proxy = ProjectionProxyFactory.create(PersonProjection.class, data);
        String json = objectMapper.writeValueAsString(proxy);

        assertTrue(json.contains("\"firstName\":\"Frank\""));
        assertTrue(json.contains("\"lastName\":\"KOSSI\""));
        assertTrue(json.contains("\"age\":30"));
        assertTrue(json.contains("\"email\":\"frank@example.com\""));
        assertTrue(json.contains("\"active\":true"));
    }

    @Test
    @DisplayName("should produce valid JSON parseable back as a Map")
    @SuppressWarnings("unchecked")
    void shouldProduceValidJson() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("firstName", "Frank");
        data.put("age", 30);

        PersonProjection proxy = ProjectionProxyFactory.create(PersonProjection.class, data);
        String json = objectMapper.writeValueAsString(proxy);

        // Parse back to Map and verify round-trip
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
        assertEquals("Frank", parsed.get("firstName"));
        assertEquals(30, parsed.get("age"));
        assertEquals(2, parsed.size());
    }
}
