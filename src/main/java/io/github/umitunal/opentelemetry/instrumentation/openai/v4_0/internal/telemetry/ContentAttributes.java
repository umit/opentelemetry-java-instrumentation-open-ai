package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry;

import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.booleanValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.call;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.doubleValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.longValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.stringListValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.stringValue;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.GenAiJson;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;

public final class ContentAttributes {
  public static final String GEN_AI_INPUT_MESSAGES = "gen_ai.input.messages";
  public static final String GEN_AI_OUTPUT_MESSAGES = "gen_ai.output.messages";
  public static final String GEN_AI_TOOL_DEFINITIONS = "gen_ai.tool.definitions";

  private ContentAttributes() {}

  public static Attributes attributes(
      Object params,
      RequestInfo request,
      ResponseTelemetry response,
      String errorType) {
    ContentAttributesBuilder builder = new ContentAttributesBuilder();
    builder.put(GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME.getKey(), "chat");
    builder.put(GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME.getKey(), "openai");
    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL.getKey(), request.model());
    builder.put("error.type", errorType);

    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT.getKey(), longValue(call(params, "n")));
    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_SEED.getKey(), longValue(call(params, "seed")));
    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM.getKey(), booleanValue(call(params, "stream")));
    builder.put(
        GenAiIncubatingAttributes.GEN_AI_REQUEST_FREQUENCY_PENALTY.getKey(),
        doubleValue(call(params, "frequencyPenalty")));
    Long maxTokens = longValue(call(params, "maxCompletionTokens"));
    if (maxTokens == null) {
      maxTokens = longValue(call(params, "maxTokens"));
    }
    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS.getKey(), maxTokens);
    builder.put(
        GenAiIncubatingAttributes.GEN_AI_REQUEST_PRESENCE_PENALTY.getKey(),
        doubleValue(call(params, "presencePenalty")));
    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE.getKey(), doubleValue(call(params, "temperature")));
    builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P.getKey(), doubleValue(call(params, "topP")));
    builder.put(
        GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES.getKey(),
        stringListValue(call(params, "stop")));
    builder.put(GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE.getKey(), stringValue(call(call(params, "responseFormat"), "type")));

    builder.put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID.getKey(), response.responseId());
    builder.put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL.getKey(), response.responseModel());
    builder.put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS.getKey(), response.finishReasons());
    builder.put(GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS.getKey(), response.inputTokens());
    builder.put(GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS.getKey(), response.outputTokens());

    builder.put(GEN_AI_INPUT_MESSAGES, GenAiJson.toJson(call(params, "messages")));
    builder.put(GEN_AI_TOOL_DEFINITIONS, GenAiJson.toJson(call(params, "tools")));
    builder.put(GEN_AI_OUTPUT_MESSAGES, GenAiJson.toJson(response.outputMessages()));
    return builder.build();
  }

  private static final class ContentAttributesBuilder {
    private final io.opentelemetry.api.common.AttributesBuilder delegate = Attributes.builder();

    private void put(String key, String value) {
      if (value != null && !value.isBlank()) {
        delegate.put(key, value);
      }
    }

    private void put(String key, Long value) {
      if (value != null) {
        delegate.put(key, value);
      }
    }

    private void put(String key, Double value) {
      if (value != null) {
        delegate.put(key, value);
      }
    }

    private void put(String key, Boolean value) {
      if (value != null) {
        delegate.put(key, value);
      }
    }

    private void put(String key, java.util.List<String> value) {
      if (value != null && !value.isEmpty()) {
        delegate.put(key, value.toArray(String[]::new));
      }
    }

    private Attributes build() {
      return delegate.build();
    }
  }
}
