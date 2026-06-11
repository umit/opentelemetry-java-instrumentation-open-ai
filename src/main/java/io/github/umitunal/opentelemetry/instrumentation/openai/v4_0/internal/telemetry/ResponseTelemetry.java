package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry;

import java.util.List;

public record ResponseTelemetry(
    String responseId,
    String responseModel,
    String serviceTier,
    String systemFingerprint,
    Long inputTokens,
    Long outputTokens,
    List<String> finishReasons,
    List<Object> outputMessages) {
  public static final ResponseTelemetry EMPTY =
      new ResponseTelemetry(null, null, null, null, null, null, List.of(), List.of());

  public ResponseTelemetry {
    finishReasons = finishReasons == null ? List.of() : List.copyOf(finishReasons);
    outputMessages = outputMessages == null ? List.of() : List.copyOf(outputMessages);
  }
}
