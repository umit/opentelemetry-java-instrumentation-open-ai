package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.OpenAiTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;

public final class OpenAiMetrics {
  private final LongHistogram tokenUsage;
  private final DoubleHistogram operationDuration;
  private final DoubleHistogram timeToFirstChunk;
  private final DoubleHistogram timePerOutputChunk;

  public OpenAiMetrics(
      LongHistogram tokenUsage,
      DoubleHistogram operationDuration,
      DoubleHistogram timeToFirstChunk,
      DoubleHistogram timePerOutputChunk) {
    this.tokenUsage = tokenUsage;
    this.operationDuration = operationDuration;
    this.timeToFirstChunk = timeToFirstChunk;
    this.timePerOutputChunk = timePerOutputChunk;
  }

  public void recordTokenUsage(RequestInfo request, ResponseTelemetry response) {
    if (response.inputTokens() != null) {
      tokenUsage.record(response.inputTokens(), attributes(request, response, "input", null));
    }
    if (response.outputTokens() != null) {
      tokenUsage.record(response.outputTokens(), attributes(request, response, "output", null));
    }
  }

  public void recordOperationDuration(
      RequestInfo request,
      ResponseTelemetry response,
      String errorType,
      long startNanos) {
    operationDuration.record(secondsSince(startNanos), attributes(request, response, null, errorType));
  }

  public void recordTimeToFirstChunk(RequestInfo request, ResponseTelemetry response, double seconds) {
    timeToFirstChunk.record(seconds, attributes(request, response, null, null));
  }

  public void recordTimePerOutputChunk(RequestInfo request, ResponseTelemetry response, double seconds) {
    timePerOutputChunk.record(seconds, attributes(request, response, null, null));
  }

  static double secondsSince(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000_000.0d;
  }

  private static Attributes attributes(
      RequestInfo request,
      ResponseTelemetry response,
      String tokenType,
      String errorType) {
    AttributesBuilder builder = Attributes.builder()
        .put(
            GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME,
            GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT)
        .put(
            GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME,
            GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI);
    if (request.model() != null) {
      builder.put(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL, request.model());
    }
    if (response != null && response.responseModel() != null) {
      builder.put(GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL, response.responseModel());
    }
    if (tokenType != null) {
      builder.put(GenAiIncubatingAttributes.GEN_AI_TOKEN_TYPE, tokenType);
    }
    if (errorType != null) {
      builder.put("error.type", errorType);
    }
    return builder.build();
  }
}
