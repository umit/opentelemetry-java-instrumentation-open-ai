package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenAiJsonTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void serializesNestedToolArguments() throws IOException {
    String json = GenAiJson.toJson(
        Map.of(
            "tool", "get_http_cat",
            "arguments", Map.of("status_code", 404)));

    assertEquals(
        MAPPER.readTree("{\"arguments\":{\"status_code\":404},\"tool\":\"get_http_cat\"}"),
        MAPPER.readTree(json));
  }
}
