package dev.jordy.jordylab.shared.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-02T10:20:00Z");

    @Test
    void objectMapperSerialisesJavaTimeInstants() throws JsonProcessingException {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();

        String json = objectMapper.writeValueAsString(Map.of("capturedAt", CAPTURED_AT));

        assertThat(objectMapper.readTree(json).get("capturedAt").isNumber()).isTrue();
    }

    @Test
    void serialisingTheSameInstantTwiceIsDeterministic() throws JsonProcessingException {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();

        assertThat(objectMapper.writeValueAsString(CAPTURED_AT))
                .isEqualTo(objectMapper.writeValueAsString(CAPTURED_AT));
    }
}
