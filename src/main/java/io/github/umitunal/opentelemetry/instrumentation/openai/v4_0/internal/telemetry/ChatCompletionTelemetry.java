package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry;

import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.call;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.listValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.longValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.setBooleanAttribute;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.setDoubleAttribute;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.setLongAttribute;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.setStringAttribute;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.stringListValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.stringValue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;
import io.opentelemetry.semconv.incubating.OpenaiIncubatingAttributes;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ChatCompletionTelemetry {
  private final Tracer tracer;
  private final OpenAiMetrics metrics;
  private final ContentEmitter contentEmitter;
  private final StreamingTelemetry streamingTelemetry;

  public ChatCompletionTelemetry(Tracer tracer, OpenAiMetrics metrics, ContentEmitter contentEmitter) {
    this.tracer = tracer;
    this.metrics = metrics;
    this.contentEmitter = contentEmitter;
    this.streamingTelemetry = new StreamingTelemetry(metrics, this::captureResponse, contentEmitter);
  }

  public Object create(Object delegate, Method method, Object[] args) throws Throwable {
    Object params = args[0];
    RequestInfo request = requestInfo(params);
    Span span = startChatSpan(request);
    captureRequest(span, params);

    Context context = Context.current().with(span);

    long startNanos = System.nanoTime();
    ResponseTelemetry responseTelemetry = ResponseTelemetry.EMPTY;
    String errorType = null;
    try (Scope ignored = context.makeCurrent()) {
      Object response = ReflectionUtil.invoke(delegate, method, args);
      responseTelemetry = captureResponse(span, context, response);
      metrics.recordTokenUsage(request, responseTelemetry);
      return response;
    } catch (Throwable t) {
      errorType = captureError(span, t);
      throw t;
    } finally {
      metrics.recordOperationDuration(request, responseTelemetry, errorType, startNanos);
      contentEmitter.emit(context, ContentAttributes.attributes(params, request, responseTelemetry, errorType));
      span.end();
    }
  }

  public Object createAsync(Object delegate, Method method, Object[] args) throws Throwable {
    Object params = args[0];
    RequestInfo request = requestInfo(params);
    Span span = startChatSpan(request);
    captureRequest(span, params);

    Context context = Context.current().with(span);

    long startNanos = System.nanoTime();
    try (Scope ignored = context.makeCurrent()) {
      CompletableFuture<?> future = (CompletableFuture<?>) ReflectionUtil.invoke(delegate, method, args);
      return future.whenComplete((response, throwable) -> {
        ResponseTelemetry responseTelemetry = ResponseTelemetry.EMPTY;
        String errorType = null;
        try (Scope callbackScope = context.makeCurrent()) {
          if (throwable == null) {
            responseTelemetry = captureResponse(span, context, response);
            metrics.recordTokenUsage(request, responseTelemetry);
          } else {
            errorType = captureError(span, throwable);
          }
        } finally {
          metrics.recordOperationDuration(request, responseTelemetry, errorType, startNanos);
          contentEmitter.emit(context, ContentAttributes.attributes(params, request, responseTelemetry, errorType));
          span.end();
        }
      });
    } catch (Throwable t) {
      String errorType = captureError(span, t);
      metrics.recordOperationDuration(request, ResponseTelemetry.EMPTY, errorType, startNanos);
      contentEmitter.emit(context, ContentAttributes.attributes(params, request, ResponseTelemetry.EMPTY, errorType));
      span.end();
      throw t;
    }
  }

  public Object createStreaming(Object delegate, Method method, Object[] args) throws Throwable {
    Object params = args[0];
    RequestInfo request = requestInfo(params);
    Span span = startChatSpan(request);
    captureRequest(span, params);
    span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM, true);

    Context context = Context.current().with(span);

    long startNanos = System.nanoTime();
    try (Scope ignored = context.makeCurrent()) {
      Object response = ReflectionUtil.invoke(delegate, method, args);
      return streamingTelemetry.wrap(response, span, context, request, params, startNanos);
    } catch (Throwable t) {
      String errorType = captureError(span, t);
      metrics.recordOperationDuration(request, ResponseTelemetry.EMPTY, errorType, startNanos);
      contentEmitter.emit(context, ContentAttributes.attributes(params, request, ResponseTelemetry.EMPTY, errorType));
      span.end();
      throw t;
    }
  }

  private RequestInfo requestInfo(Object params) {
    return new RequestInfo(stringValue(call(params, "model")));
  }

  private Span startChatSpan(RequestInfo request) {
    String spanName = request.model() == null || request.model().isBlank() ? "chat" : "chat " + request.model();
    Span span = tracer.spanBuilder(spanName)
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(
            GenAiIncubatingAttributes.GEN_AI_PROVIDER_NAME,
            GenAiIncubatingAttributes.GenAiProviderNameIncubatingValues.OPENAI)
        .setAttribute(
            GenAiIncubatingAttributes.GEN_AI_OPERATION_NAME,
            GenAiIncubatingAttributes.GenAiOperationNameIncubatingValues.CHAT)
        .setAttribute(
            OpenaiIncubatingAttributes.OPENAI_API_TYPE,
            OpenaiIncubatingAttributes.OpenaiApiTypeIncubatingValues.CHAT_COMPLETIONS)
        .startSpan();
    if (request.model() != null) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_MODEL, request.model());
    }
    return span;
  }

  private void captureRequest(Span span, Object params) {
    setChoiceCount(span, call(params, "n"));
    setLongAttribute(span, GenAiIncubatingAttributes.GEN_AI_REQUEST_SEED, call(params, "seed"));
    setBooleanAttribute(span, GenAiIncubatingAttributes.GEN_AI_REQUEST_STREAM, call(params, "stream"));
    setDoubleAttribute(
        span,
        GenAiIncubatingAttributes.GEN_AI_REQUEST_FREQUENCY_PENALTY,
        call(params, "frequencyPenalty"));
    setLongAttribute(span, GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS, call(params, "maxCompletionTokens"));
    if (longValue(call(params, "maxCompletionTokens")) == null) {
      setLongAttribute(span, GenAiIncubatingAttributes.GEN_AI_REQUEST_MAX_TOKENS, call(params, "maxTokens"));
    }
    setDoubleAttribute(
        span,
        GenAiIncubatingAttributes.GEN_AI_REQUEST_PRESENCE_PENALTY,
        call(params, "presencePenalty"));
    setDoubleAttribute(span, GenAiIncubatingAttributes.GEN_AI_REQUEST_TEMPERATURE, call(params, "temperature"));
    setDoubleAttribute(span, GenAiIncubatingAttributes.GEN_AI_REQUEST_TOP_P, call(params, "topP"));
    setStringAttribute(span, OpenaiIncubatingAttributes.OPENAI_REQUEST_SERVICE_TIER, call(params, "serviceTier"));

    List<String> stopSequences = stringListValue(call(params, "stop"));
    if (!stopSequences.isEmpty()) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_STOP_SEQUENCES, stopSequences);
    }

    Object responseFormat = call(params, "responseFormat");
    String responseFormatType = stringValue(call(responseFormat, "type"));
    if (responseFormatType != null) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_OUTPUT_TYPE, responseFormatType);
    }
  }

  private void setChoiceCount(Span span, Object value) {
    Long choiceCount = longValue(value);
    if (choiceCount != null && choiceCount != 1L) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_REQUEST_CHOICE_COUNT, choiceCount);
    }
  }

  public ResponseTelemetry captureResponse(Span span, Context context, Object response) {
    String id = stringValue(call(response, "id"));
    if (id != null) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_ID, id);
    }
    String model = stringValue(call(response, "model"));
    if (model != null) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_MODEL, model);
    }
    String serviceTier = stringValue(call(response, "serviceTier"));
    if (serviceTier != null) {
      span.setAttribute(OpenaiIncubatingAttributes.OPENAI_RESPONSE_SERVICE_TIER, serviceTier);
    }
    String systemFingerprint = stringValue(call(response, "systemFingerprint"));
    if (systemFingerprint != null) {
      span.setAttribute(OpenaiIncubatingAttributes.OPENAI_RESPONSE_SYSTEM_FINGERPRINT, systemFingerprint);
    }

    Object usage = call(response, "usage");
    Long inputTokens = longValue(call(usage, "promptTokens"));
    if (inputTokens == null) {
      inputTokens = longValue(call(usage, "inputTokens"));
    }
    Long outputTokens = longValue(call(usage, "completionTokens"));
    if (outputTokens == null) {
      outputTokens = longValue(call(usage, "outputTokens"));
    }
    if (inputTokens != null) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_USAGE_INPUT_TOKENS, inputTokens);
    }
    if (outputTokens != null) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_USAGE_OUTPUT_TOKENS, outputTokens);
    }
    Object promptDetails = call(usage, "promptTokensDetails");
    setLongAttribute(
        span,
        GenAiIncubatingAttributes.GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS,
        call(promptDetails, "cachedTokens"));
    setLongAttribute(
        span,
        GenAiIncubatingAttributes.GEN_AI_USAGE_CACHE_CREATION_INPUT_TOKENS,
        call(promptDetails, "cacheCreationTokens"));

    Object completionDetails = call(usage, "completionTokensDetails");
    setLongAttribute(
        span,
        GenAiIncubatingAttributes.GEN_AI_USAGE_REASONING_OUTPUT_TOKENS,
        call(completionDetails, "reasoningTokens"));

    List<String> finishReasons = new ArrayList<>();
    List<Object> outputMessages = new ArrayList<>();
    for (Object choice : listValue(call(response, "choices"))) {
      String finishReason = stringValue(call(choice, "finishReason"));
      if (finishReason != null) {
        finishReasons.add(finishReason.toLowerCase(Locale.ROOT));
      }
      Object message = call(choice, "message");
      if (message != null) {
        outputMessages.add(message);
      }
    }
    if (!finishReasons.isEmpty()) {
      span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS, finishReasons);
    }
    return new ResponseTelemetry(
        id,
        model,
        serviceTier,
        systemFingerprint,
        inputTokens,
        outputTokens,
        finishReasons,
        outputMessages);
  }

  public static String captureError(Span span, Throwable t) {
    span.recordException(t);
    span.setStatus(StatusCode.ERROR);
    Long statusCode = longValue(call(t, "statusCode"));
    String errorType = statusCode == null ? t.getClass().getName() : String.valueOf(statusCode);
    span.setAttribute("error.type", errorType);
    if (statusCode != null) {
      span.setAttribute("http.response.status_code", statusCode);
    }
    return errorType;
  }
}
