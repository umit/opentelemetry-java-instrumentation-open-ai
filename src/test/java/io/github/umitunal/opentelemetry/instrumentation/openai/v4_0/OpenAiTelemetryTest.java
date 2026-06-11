package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiTelemetryTest {
  @AfterEach
  void clearContentCaptureProperty() {
    System.clearProperty("otel.instrumentation.openai.content.capture.mode");
  }

  @Test
  void wrapsChatCompletionCreateWithGenAiSpan() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    Object response = client.chat().completions().create(new FakeParams("deepseek-chat"));

    assertEquals("chatcmpl-test", response.getClass().cast(response).toString());
    var span = spans.getFinishedSpanItems().get(0);
    assertEquals("chat deepseek-chat", span.getName());
    assertEquals(io.opentelemetry.api.trace.SpanKind.CLIENT, span.getKind());
    assertEquals("openai", span.getAttributes().get(AttributeKey.stringKey("gen_ai.provider.name")));
    assertEquals("chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals("chat_completions", span.getAttributes().get(AttributeKey.stringKey("openai.api.type")));
    assertEquals("deepseek-chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
    assertEquals(2L, span.getAttributes().get(AttributeKey.longKey("gen_ai.request.choice.count")));
    assertEquals(42L, span.getAttributes().get(AttributeKey.longKey("gen_ai.request.seed")));
    assertEquals(false, span.getAttributes().get(AttributeKey.booleanKey("gen_ai.request.stream")));
    assertEquals(0.2d, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.frequency_penalty")));
    assertEquals(256L, span.getAttributes().get(AttributeKey.longKey("gen_ai.request.max_tokens")));
    assertEquals(0.3d, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.presence_penalty")));
    assertEquals(0.7d, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.temperature")));
    assertEquals(0.9d, span.getAttributes().get(AttributeKey.doubleKey("gen_ai.request.top_p")));
    assertEquals("auto", span.getAttributes().get(AttributeKey.stringKey("openai.request.service_tier")));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringArrayKey("gen_ai.request.stop_sequences"))
        .contains("END"));
    assertEquals("chatcmpl-test", span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));
    assertEquals("deepseek-v4", span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")));
    assertEquals("default", span.getAttributes().get(AttributeKey.stringKey("openai.response.service_tier")));
    assertEquals("fp-test", span.getAttributes().get(AttributeKey.stringKey("openai.response.system_fingerprint")));
    assertEquals(12L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    assertEquals(7L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
    assertEquals(3L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.cache_read.input_tokens")));
    assertEquals(2L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.cache_creation.input_tokens")));
    assertEquals(5L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.reasoning.output_tokens")));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons"))
        .contains("tool_calls"));
  }

  @Test
  void doesNotEmitLogRecordsWhenContentCaptureUsesEvents() {
    System.setProperty("otel.instrumentation.openai.content.capture.mode", "event");
    InMemoryLogRecordExporter logs = InMemoryLogRecordExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logs))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    assertTrue(logs.getFinishedLogRecordItems().isEmpty());
  }

  @Test
  void doesNotCaptureContentByDefault() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    var span = spans.getFinishedSpanItems().get(0);
    assertTrue(span.getEvents().isEmpty());
    assertEquals(null, span.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages")));
    assertEquals(null, span.getAttributes().get(AttributeKey.stringKey("gen_ai.output.messages")));
    assertEquals(null, span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.definitions")));
  }

  @Test
  void eventCaptureModeEmitsInferenceOperationDetailsEventOnly() {
    System.setProperty("otel.instrumentation.openai.content.capture.mode", "event");
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    var span = spans.getFinishedSpanItems().get(0);
    assertEquals(null, span.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages")));
    assertEquals(1, span.getEvents().size());
    var event = span.getEvents().get(0);
    assertEquals("gen_ai.client.inference.operation.details", event.getName());
    assertEquals("chat", event.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals("deepseek-chat", event.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
    assertTrue(event.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages")).contains("user: 404"));
    assertTrue(event.getAttributes()
        .get(AttributeKey.stringKey("gen_ai.output.messages"))
        .contains("assistant: call tool"));
    assertTrue(event.getAttributes()
        .get(AttributeKey.stringKey("gen_ai.tool.definitions"))
        .contains("get_http_cat"));
  }

  @Test
  void spanAttributeCaptureModeEmitsCapturedContentAsSpanAttributesOnly() {
    System.setProperty("otel.instrumentation.openai.content.capture.mode", "span_attribute");
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    var span = spans.getFinishedSpanItems().get(0);
    assertTrue(span.getEvents().isEmpty());
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages")).contains("user: 404"));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringKey("gen_ai.output.messages"))
        .contains("assistant: call tool"));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringKey("gen_ai.tool.definitions"))
        .contains("get_http_cat"));
  }

  @Test
  void bothCaptureModeEmitsContentAsEventAndSpanAttributes() {
    System.setProperty("otel.instrumentation.openai.content.capture.mode", "both");
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    var span = spans.getFinishedSpanItems().get(0);
    assertEquals(1, span.getEvents().size());
    assertEquals("gen_ai.client.inference.operation.details", span.getEvents().get(0).getName());
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages")).contains("user: 404"));
  }

  @Test
  void wrapsAsyncChatCompletionCreateWithGenAiSpan() {
    System.setProperty("otel.instrumentation.openai.content.capture.mode", "span_attribute");
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    Object response = client.async()
        .chat()
        .completions()
        .create(new FakeParams("deepseek-chat"))
        .join();

    assertEquals("chatcmpl-test", response.getClass().cast(response).toString());
    var span = spans.getFinishedSpanItems().get(0);
    assertEquals("chat deepseek-chat", span.getName());
    assertEquals(io.opentelemetry.api.trace.SpanKind.CLIENT, span.getKind());
    assertEquals("chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
    assertEquals("deepseek-chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.request.model")));
    assertEquals("deepseek-v4", span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")));
    assertEquals(12L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    assertEquals(7L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
    assertTrue(span.getAttributes().get(AttributeKey.stringKey("gen_ai.input.messages")).contains("user: 404"));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringKey("gen_ai.output.messages"))
        .contains("assistant: call tool"));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringKey("gen_ai.tool.definitions"))
        .contains("get_http_cat"));
  }

  @Test
  void doesNotEmitContentLogsByDefault() {
    InMemoryLogRecordExporter logs = InMemoryLogRecordExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    SdkLoggerProvider loggerProvider = SdkLoggerProvider.builder()
        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logs))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    assertTrue(logs.getFinishedLogRecordItems().isEmpty());
  }

  @Test
  void recordsExceptionAndErrorStatusWhenCreateFails() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();
    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FailingOpenAIClient());

    assertThrows(IllegalStateException.class,
        () -> client.chat().completions().create(new FakeParams("deepseek-chat")));

    var span = spans.getFinishedSpanItems().get(0);
    assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.getStatus().getStatusCode());
    assertEquals(IllegalStateException.class.getName(),
        span.getAttributes().get(AttributeKey.stringKey("error.type")));
  }

  @Test
  void recordsRateLimitErrorsOnSpanAndMetrics() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    InMemoryMetricReader metrics = InMemoryMetricReader.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    SdkMeterProvider meterProvider = SdkMeterProvider.builder()
        .registerMetricReader(metrics)
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .build();
    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new RateLimitedOpenAIClient());

    assertThrows(FakeRateLimitException.class,
        () -> client.chat().completions().create(new FakeParams("deepseek-chat")));

    var span = spans.getFinishedSpanItems().get(0);
    assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.getStatus().getStatusCode());
    assertEquals("429", span.getAttributes().get(AttributeKey.stringKey("error.type")));
    assertEquals(429L, span.getAttributes().get(AttributeKey.longKey("http.response.status_code")));

    HistogramPointData durationPoint = histogramPoint(
        metrics.collectAllMetrics(),
        OpenAiTelemetry.METRIC_GEN_AI_CLIENT_OPERATION_DURATION,
        Attributes.builder()
            .put("gen_ai.operation.name", "chat")
            .put("gen_ai.provider.name", "openai")
            .put("gen_ai.request.model", "deepseek-chat")
            .put("error.type", "429")
            .build());
    assertEquals(1L, durationPoint.getCount());
    assertTrue(durationPoint.getSum() >= 0.0d);
  }

  @Test
  void recordsGenAiClientMetricsForCreate() {
    InMemoryMetricReader metrics = InMemoryMetricReader.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    SdkMeterProvider meterProvider = SdkMeterProvider.builder()
        .registerMetricReader(metrics)
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    client.chat().completions().create(new FakeParams("deepseek-chat"));

    Collection<MetricData> data = metrics.collectAllMetrics();
    HistogramPointData inputTokens = histogramPoint(
        data,
        OpenAiTelemetry.METRIC_GEN_AI_CLIENT_TOKEN_USAGE,
        Attributes.builder()
            .put("gen_ai.operation.name", "chat")
            .put("gen_ai.provider.name", "openai")
            .put("gen_ai.request.model", "deepseek-chat")
            .put("gen_ai.response.model", "deepseek-v4")
            .put("gen_ai.token.type", "input")
            .build());
    HistogramPointData outputTokens = histogramPoint(
        data,
        OpenAiTelemetry.METRIC_GEN_AI_CLIENT_TOKEN_USAGE,
        Attributes.builder()
            .put("gen_ai.operation.name", "chat")
            .put("gen_ai.provider.name", "openai")
            .put("gen_ai.request.model", "deepseek-chat")
            .put("gen_ai.response.model", "deepseek-v4")
            .put("gen_ai.token.type", "output")
            .build());
    HistogramPointData duration = histogramPoint(
        data,
        OpenAiTelemetry.METRIC_GEN_AI_CLIENT_OPERATION_DURATION,
        Attributes.builder()
            .put("gen_ai.operation.name", "chat")
            .put("gen_ai.provider.name", "openai")
            .put("gen_ai.request.model", "deepseek-chat")
            .put("gen_ai.response.model", "deepseek-v4")
            .build());

    assertEquals(12.0d, inputTokens.getSum());
    assertEquals(7.0d, outputTokens.getSum());
    assertEquals(1L, duration.getCount());
    assertTrue(duration.getSum() >= 0.0d);
  }

  @Test
  void wrapsChatCompletionCreateStreamingUntilStreamEnds() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    FakeStreamResponse response = (FakeStreamResponse) client.chat()
        .completions()
        .createStreaming(new FakeParams("deepseek-chat"));

    assertTrue(spans.getFinishedSpanItems().isEmpty());
    try (Stream<Object> stream = response.stream()) {
      assertEquals(2, stream.toList().size());
    }

    var span = spans.getFinishedSpanItems().get(0);
    assertEquals("chat deepseek-chat", span.getName());
    assertEquals(true, span.getAttributes().get(AttributeKey.booleanKey("gen_ai.request.stream")));
    assertEquals("chatcmpl-stream", span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id")));
    assertEquals("deepseek-v4-stream", span.getAttributes().get(AttributeKey.stringKey("gen_ai.response.model")));
    assertEquals("default", span.getAttributes().get(AttributeKey.stringKey("openai.response.service_tier")));
    assertEquals("fp-stream", span.getAttributes().get(AttributeKey.stringKey("openai.response.system_fingerprint")));
    assertEquals(30L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
    assertEquals(9L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.output_tokens")));
    assertEquals(1L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.cache_read.input_tokens")));
    assertTrue(span.getAttributes()
        .get(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons"))
        .contains("stop"));
    assertTrue(span.getAttributes()
        .get(AttributeKey.doubleKey("gen_ai.response.time_to_first_chunk")) >= 0.0d);
  }

  @Test
  void recordsExceptionWhenStreamingConsumptionFails() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(spans))
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new StreamingFailingOpenAIClient());
    FakeStreamResponse response = (FakeStreamResponse) client.chat()
        .completions()
        .createStreaming(new FakeParams("deepseek-chat"));

    IllegalStateException error;
    try (Stream<Object> stream = response.stream()) {
      error = assertThrows(IllegalStateException.class, () -> stream.forEach(item -> {}));
    }
    assertEquals("stream boom", error.getMessage());

    var span = spans.getFinishedSpanItems().get(0);
    assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.getStatus().getStatusCode());
    assertEquals(IllegalStateException.class.getName(),
        span.getAttributes().get(AttributeKey.stringKey("error.type")));
  }

  @Test
  void recordsStreamingMetricsUntilStreamEnds() {
    InMemoryMetricReader metrics = InMemoryMetricReader.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
    SdkMeterProvider meterProvider = SdkMeterProvider.builder()
        .registerMetricReader(metrics)
        .build();
    OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setMeterProvider(meterProvider)
        .build();

    OpenAIClient client = (OpenAIClient) OpenAiTelemetry.create(openTelemetry).wrap(new FakeOpenAIClient());
    FakeStreamResponse response = (FakeStreamResponse) client.chat()
        .completions()
        .createStreaming(new FakeParams("deepseek-chat"));

    try (Stream<Object> stream = response.stream()) {
      stream.toList();
    }

    Collection<MetricData> data = metrics.collectAllMetrics();
    assertTrue(metricNames(data).contains(OpenAiTelemetry.METRIC_GEN_AI_CLIENT_OPERATION_TIME_TO_FIRST_CHUNK));
    assertTrue(metricNames(data).contains(OpenAiTelemetry.METRIC_GEN_AI_CLIENT_OPERATION_TIME_PER_OUTPUT_CHUNK));
    assertTrue(metricNames(data).contains(OpenAiTelemetry.METRIC_GEN_AI_CLIENT_TOKEN_USAGE));
    assertTrue(metricNames(data).contains(OpenAiTelemetry.METRIC_GEN_AI_CLIENT_OPERATION_DURATION));
  }

  private static List<String> metricNames(Collection<MetricData> metrics) {
    return metrics.stream().map(MetricData::getName).toList();
  }

  private static HistogramPointData histogramPoint(
      Collection<MetricData> metrics,
      String name,
      Attributes requiredAttributes) {
    return metrics.stream()
        .filter(metric -> metric.getName().equals(name))
        .flatMap(metric -> metric.getHistogramData().getPoints().stream())
        .filter(point -> containsAll(point.getAttributes(), requiredAttributes))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing metric point " + name + " " + requiredAttributes));
  }

  private static boolean containsAll(Attributes actual, Attributes required) {
    for (var entry : required.asMap().entrySet()) {
      if (!entry.getValue().equals(actual.get(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  record FakeParams(String model) {
    List<String> messages() {
      return List.of("user: 404");
    }

    List<String> tools() {
      return List.of("get_http_cat");
    }

    long n() {
      return 2;
    }

    long seed() {
      return 42;
    }

    boolean stream() {
      return false;
    }

    double frequencyPenalty() {
      return 0.2d;
    }

    long maxCompletionTokens() {
      return 256;
    }

    double presencePenalty() {
      return 0.3d;
    }

    List<String> stop() {
      return List.of("END", "STOP");
    }

    double temperature() {
      return 0.7d;
    }

    double topP() {
      return 0.9d;
    }

    String serviceTier() {
      return "auto";
    }
  }

  record OptionalModelParams(Optional<String> model) {}

  static final class FakeOpenAIClient implements OpenAIClient {
    @Override
    public OpenAIClientAsync async() {
      return new FakeOpenAIClientAsync();
    }

    @Override
    public ChatService chat() {
      return new FakeChatService();
    }
  }

  static final class FakeOpenAIClientAsync implements OpenAIClientAsync {
    @Override
    public ChatServiceAsync chat() {
      return new FakeChatServiceAsync();
    }
  }

  static final class FakeChatService implements ChatService {
    @Override
    public ChatCompletionService completions() {
      return new FakeChatCompletionService();
    }
  }

  static final class FakeChatServiceAsync implements ChatServiceAsync {
    @Override
    public ChatCompletionServiceAsync completions() {
      return new FakeChatCompletionServiceAsync();
    }
  }

  static final class FakeChatCompletionService implements ChatCompletionService {
    @Override
    public Object create(Object params) {
      return new FakeResponse();
    }

    @Override
    public Object createStreaming(Object params) {
      return new FakeStreamingResponse();
    }
  }

  static final class FakeChatCompletionServiceAsync implements ChatCompletionServiceAsync {
    @Override
    public CompletableFuture<Object> create(Object params) {
      return CompletableFuture.completedFuture(new FakeResponse());
    }
  }

  static final class FailingOpenAIClient implements OpenAIClient {
    @Override
    public ChatService chat() {
      return new FailingChatService();
    }
  }

  static final class FailingChatService implements ChatService {
    @Override
    public ChatCompletionService completions() {
      return new FailingChatCompletionService();
    }
  }

  static final class FailingChatCompletionService implements ChatCompletionService {
    @Override
    public Object create(Object params) {
      throw new IllegalStateException("boom");
    }

    @Override
    public Object createStreaming(Object params) {
      throw new IllegalStateException("boom");
    }
  }

  static final class RateLimitedOpenAIClient implements OpenAIClient {
    @Override
    public ChatService chat() {
      return new RateLimitedChatService();
    }
  }

  static final class RateLimitedChatService implements ChatService {
    @Override
    public ChatCompletionService completions() {
      return new RateLimitedChatCompletionService();
    }
  }

  static final class RateLimitedChatCompletionService implements ChatCompletionService {
    @Override
    public Object create(Object params) {
      throw new FakeRateLimitException();
    }

    @Override
    public Object createStreaming(Object params) {
      throw new FakeRateLimitException();
    }
  }

  static final class FakeRateLimitException extends RuntimeException {
    int statusCode() {
      return 429;
    }
  }

  static final class StreamingFailingOpenAIClient implements OpenAIClient {
    @Override
    public ChatService chat() {
      return new StreamingFailingChatService();
    }
  }

  static final class StreamingFailingChatService implements ChatService {
    @Override
    public ChatCompletionService completions() {
      return new StreamingFailingChatCompletionService();
    }
  }

  static final class StreamingFailingChatCompletionService implements ChatCompletionService {
    @Override
    public Object create(Object params) {
      return new FakeResponse();
    }

    @Override
    public Object createStreaming(Object params) {
      return new FailingStreamingResponse();
    }
  }

  static final class FakeResponse {
    String id() {
      return "chatcmpl-test";
    }

    String model() {
      return "deepseek-v4";
    }

    List<FakeChoice> choices() {
      return List.of(new FakeChoice());
    }

    FakeUsage usage() {
      return new FakeUsage();
    }

    String serviceTier() {
      return "default";
    }

    String systemFingerprint() {
      return "fp-test";
    }

    @Override
    public String toString() {
      return id();
    }
  }

  static final class FakeChoice {
    String finishReason() {
      return "TOOL_CALLS";
    }

    String message() {
      return "assistant: call tool";
    }
  }

  static final class FakeUsage {
    long promptTokens() {
      return 12;
    }

    long completionTokens() {
      return 7;
    }

    FakePromptTokensDetails promptTokensDetails() {
      return new FakePromptTokensDetails();
    }

    FakeCompletionTokensDetails completionTokensDetails() {
      return new FakeCompletionTokensDetails();
    }
  }

  static final class FakePromptTokensDetails {
    long cachedTokens() {
      return 3;
    }

    long cacheCreationTokens() {
      return 2;
    }
  }

  static final class FakeCompletionTokensDetails {
    long reasoningTokens() {
      return 5;
    }
  }

  interface FakeStreamResponse extends AutoCloseable {
    Stream<Object> stream();

    @Override
    void close();
  }

  static final class FakeStreamingResponse implements FakeStreamResponse {
    @Override
    public Stream<Object> stream() {
      return Stream.of(
          new FakeChunk("chatcmpl-stream", "deepseek-v4-stream", null, null),
          new FakeChunk("chatcmpl-stream", "deepseek-v4-stream", "STOP", new FakeStreamingUsage()));
    }

    @Override
    public void close() {}
  }

  static final class FailingStreamingResponse implements FakeStreamResponse {
    @Override
    public Stream<Object> stream() {
      Spliterator<Object> spliterator = new Spliterator<>() {
        @Override
        public boolean tryAdvance(Consumer<? super Object> action) {
          throw new IllegalStateException("stream boom");
        }

        @Override
        public Spliterator<Object> trySplit() {
          return null;
        }

        @Override
        public long estimateSize() {
          return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
          return 0;
        }
      };
      return StreamSupport.stream(spliterator, false);
    }

    @Override
    public void close() {}
  }

  record FakeChunk(String id, String model, String finishReason, Object usage) {
    List<FakeStreamingChoice> choices() {
      return finishReason == null ? List.of() : List.of(new FakeStreamingChoice(finishReason));
    }

    String serviceTier() {
      return "default";
    }

    String systemFingerprint() {
      return "fp-stream";
    }
  }

  record FakeStreamingChoice(String finishReason) {
    String delta() {
      return "assistant: streamed";
    }
  }

  static final class FakeStreamingUsage {
    long promptTokens() {
      return 30;
    }

    long completionTokens() {
      return 9;
    }

    FakeStreamingPromptTokensDetails promptTokensDetails() {
      return new FakeStreamingPromptTokensDetails();
    }
  }

  static final class FakeStreamingPromptTokensDetails {
    long cachedTokens() {
      return 1;
    }
  }
}
