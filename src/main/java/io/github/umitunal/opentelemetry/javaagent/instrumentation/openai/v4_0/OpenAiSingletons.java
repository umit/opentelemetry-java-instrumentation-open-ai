package io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.OpenAiTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;

public final class OpenAiSingletons {
  private static final OpenAiTelemetry TELEMETRY =
      OpenAiTelemetry.create(GlobalOpenTelemetry.get());

  private OpenAiSingletons() {}

  public static OpenAiTelemetry telemetry() {
    return TELEMETRY;
  }
}
