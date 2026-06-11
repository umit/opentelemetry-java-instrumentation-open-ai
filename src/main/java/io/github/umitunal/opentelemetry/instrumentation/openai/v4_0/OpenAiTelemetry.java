package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ChatCompletionTelemetry;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentCaptureMode;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentEmitter;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.OpenAiMetrics;
import io.opentelemetry.context.Context;

public final class OpenAiTelemetry {
  public static final String INSTRUMENTATION_NAME = "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0";
  public static final String INSTRUMENTATION_VERSION = "0.1.0";
  public static final String METRIC_GEN_AI_CLIENT_TOKEN_USAGE = "gen_ai.client.token.usage";
  public static final String METRIC_GEN_AI_CLIENT_OPERATION_DURATION = "gen_ai.client.operation.duration";
  public static final String METRIC_GEN_AI_CLIENT_OPERATION_TIME_TO_FIRST_CHUNK =
      "gen_ai.client.operation.time_to_first_chunk";
  public static final String METRIC_GEN_AI_CLIENT_OPERATION_TIME_PER_OUTPUT_CHUNK =
      "gen_ai.client.operation.time_per_output_chunk";

  private final Tracer tracer;
  private final ContentCaptureMode contentCaptureMode;
  private final ChatCompletionTelemetry chatCompletionTelemetry;

  public static OpenAiTelemetry create(OpenTelemetry openTelemetry) {
    return new OpenAiTelemetry(openTelemetry);
  }

  OpenAiTelemetry(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry
        .tracerBuilder(INSTRUMENTATION_NAME)
        .setInstrumentationVersion(INSTRUMENTATION_VERSION)
        .build();
    this.contentCaptureMode = ContentCaptureMode.fromSystemProperty();

    Meter meter = openTelemetry
        .meterBuilder(INSTRUMENTATION_NAME)
        .setInstrumentationVersion(INSTRUMENTATION_VERSION)
        .build();
    LongHistogram tokenUsage = meter.histogramBuilder(METRIC_GEN_AI_CLIENT_TOKEN_USAGE)
        .ofLongs()
        .setUnit("{token}")
        .setDescription("Number of input and output tokens used.")
        .build();
    DoubleHistogram operationDuration = meter.histogramBuilder(METRIC_GEN_AI_CLIENT_OPERATION_DURATION)
        .setUnit("s")
        .setDescription("GenAI operation duration.")
        .build();
    DoubleHistogram timeToFirstChunk = meter.histogramBuilder(METRIC_GEN_AI_CLIENT_OPERATION_TIME_TO_FIRST_CHUNK)
        .setUnit("s")
        .setDescription("Time to first chunk in a streaming GenAI response.")
        .build();
    DoubleHistogram timePerOutputChunk = meter.histogramBuilder(METRIC_GEN_AI_CLIENT_OPERATION_TIME_PER_OUTPUT_CHUNK)
        .setUnit("s")
        .setDescription("Time between chunks in a streaming GenAI response.")
        .build();

    OpenAiMetrics metrics = new OpenAiMetrics(
        tokenUsage,
        operationDuration,
        timeToFirstChunk,
        timePerOutputChunk);
    this.chatCompletionTelemetry = new ChatCompletionTelemetry(tracer, metrics, this::emitContent);
  }

  public Object wrap(Object client) {
    return OpenAiClientProxy.wrap(client, chatCompletionTelemetry);
  }

  private void emitContent(Context context, Attributes attributes) {
    if (contentCaptureMode == ContentCaptureMode.OFF || attributes.isEmpty()) {
      return;
    }
    Span span = Span.fromContext(context);
    if (contentCaptureMode.emitsEvent()) {
      span.addEvent(ContentCaptureMode.EVENT_NAME, attributes);
    }
    if (contentCaptureMode.emitsSpanAttributes()) {
      attributes.forEach((key, value) -> {
        if (value instanceof String stringValue) {
          span.setAttribute(key.getKey(), stringValue);
        } else if (value instanceof Long longValue) {
          span.setAttribute(key.getKey(), longValue);
        } else if (value instanceof Double doubleValue) {
          span.setAttribute(key.getKey(), doubleValue);
        } else if (value instanceof Boolean booleanValue) {
          span.setAttribute(key.getKey(), booleanValue);
        }
      });
    }
  }
}
