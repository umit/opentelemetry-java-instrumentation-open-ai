package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class GenAiJson {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private GenAiJson() {}

  public static String toJson(Object value) {
    if (value == null) {
      return "null";
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }
}
