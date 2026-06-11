package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming;

import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.call;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.listValue;
import static io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil.stringValue;

import io.opentelemetry.api.trace.Span;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ChatCompletionTelemetry;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentAttributes;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentEmitter;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.OpenAiMetrics;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.RequestInfo;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ResponseTelemetry;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamingTelemetry {
  private final OpenAiMetrics metrics;
  private final ResponseCapturer responseCapturer;
  private final ContentEmitter contentEmitter;

  public StreamingTelemetry(OpenAiMetrics metrics, ResponseCapturer responseCapturer, ContentEmitter contentEmitter) {
    this.metrics = metrics;
    this.responseCapturer = responseCapturer;
    this.contentEmitter = contentEmitter;
  }

  public Object wrap(
      Object response,
      Span span,
      Context context,
      RequestInfo request,
      Object params,
      long operationStartNanos) {
    return ReflectionUtil.proxy(
        response,
        "com.openai.core.http.StreamResponse",
        new StreamResponseHandler(response, new StreamState(span, context, request, params, operationStartNanos)));
  }

  @FunctionalInterface
  public interface ResponseCapturer {
    ResponseTelemetry capture(Span span, Context context, Object response);
  }

  private final class StreamResponseHandler implements InvocationHandler {
    private final Object delegate;
    private final StreamState state;

    private StreamResponseHandler(Object delegate, StreamState state) {
      this.delegate = delegate;
      this.state = state;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (ReflectionUtil.isObjectMethod(method)) {
        return ReflectionUtil.invoke(delegate, method, args);
      }
      if (method.getName().equals("stream") && (args == null || args.length == 0)) {
        return instrumentStream((Stream<?>) ReflectionUtil.invoke(delegate, method, args));
      }
      if (method.getName().equals("close") && (args == null || args.length == 0)) {
        try {
          return ReflectionUtil.invoke(delegate, method, args);
        } catch (Throwable t) {
          state.recordException(t);
          throw t;
        } finally {
          state.end();
        }
      }
      return ReflectionUtil.invoke(delegate, method, args);
    }

    private Stream<?> instrumentStream(Stream<?> stream) {
      return StreamSupport.stream(new InstrumentedSpliterator(stream.spliterator(), state), stream.isParallel())
          .onClose(() -> {
            try {
              stream.close();
            } finally {
              state.end();
            }
          });
    }
  }

  private final class StreamState {
    private final Span span;
    private final Context context;
    private final RequestInfo request;
    private final Object params;
    private final long operationStartNanos;
    private final List<String> finishReasons = new ArrayList<>();
    private final List<Object> outputMessages = new ArrayList<>();
    private ResponseTelemetry responseTelemetry = ResponseTelemetry.EMPTY;
    private boolean firstChunkSeen;
    private long lastChunkNanos;
    private String errorType;
    private boolean ended;

    private StreamState(Span span, Context context, RequestInfo request, Object params, long operationStartNanos) {
      this.span = span;
      this.context = context;
      this.request = request;
      this.params = params;
      this.operationStartNanos = operationStartNanos;
    }

    private synchronized void captureChunk(Object chunk) {
      long now = System.nanoTime();
      if (!firstChunkSeen) {
        firstChunkSeen = true;
        double seconds = (now - operationStartNanos) / 1_000_000_000.0d;
        span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_TIME_TO_FIRST_CHUNK, seconds);
        metrics.recordTimeToFirstChunk(request, responseTelemetry, seconds);
      } else {
        double seconds = (now - lastChunkNanos) / 1_000_000_000.0d;
        metrics.recordTimePerOutputChunk(request, responseTelemetry, seconds);
      }
      lastChunkNanos = now;

      responseTelemetry = merge(responseTelemetry, responseCapturer.capture(span, context, chunk));
      for (Object choice : listValue(call(chunk, "choices"))) {
        String finishReason = stringValue(call(choice, "finishReason"));
        if (finishReason != null) {
          finishReasons.add(finishReason.toLowerCase(Locale.ROOT));
        }
        Object delta = call(choice, "delta");
        if (delta != null) {
          outputMessages.add(delta);
        }
      }
    }

    private synchronized void recordException(Throwable t) {
      errorType = ChatCompletionTelemetry.captureError(span, t);
    }

    private synchronized void end() {
      if (ended) {
        return;
      }
      ended = true;
      if (!finishReasons.isEmpty()) {
        span.setAttribute(GenAiIncubatingAttributes.GEN_AI_RESPONSE_FINISH_REASONS, finishReasons);
      }
      ResponseTelemetry finalResponseTelemetry = merge(
          responseTelemetry,
          new ResponseTelemetry(null, null, null, null, null, null, finishReasons, outputMessages));
      metrics.recordTokenUsage(request, finalResponseTelemetry);
      metrics.recordOperationDuration(request, finalResponseTelemetry, errorType, operationStartNanos);
      contentEmitter.emit(
          context,
          ContentAttributes.attributes(params, request, finalResponseTelemetry, errorType));
      span.end();
    }

    private ResponseTelemetry merge(ResponseTelemetry previous, ResponseTelemetry next) {
      return new ResponseTelemetry(
          next.responseId() == null ? previous.responseId() : next.responseId(),
          next.responseModel() == null ? previous.responseModel() : next.responseModel(),
          next.serviceTier() == null ? previous.serviceTier() : next.serviceTier(),
          next.systemFingerprint() == null ? previous.systemFingerprint() : next.systemFingerprint(),
          next.inputTokens() == null ? previous.inputTokens() : next.inputTokens(),
          next.outputTokens() == null ? previous.outputTokens() : next.outputTokens(),
          next.finishReasons().isEmpty() ? previous.finishReasons() : next.finishReasons(),
          next.outputMessages().isEmpty() ? previous.outputMessages() : next.outputMessages());
    }
  }

  private static final class InstrumentedSpliterator implements Spliterator<Object> {
    private final Spliterator<?> delegate;
    private final StreamState state;

    private InstrumentedSpliterator(Spliterator<?> delegate, StreamState state) {
      this.delegate = delegate;
      this.state = state;
    }

    @Override
    public boolean tryAdvance(Consumer<? super Object> action) {
      try {
        boolean advanced = delegate.tryAdvance(item -> {
          state.captureChunk(item);
          action.accept(item);
        });
        if (!advanced) {
          state.end();
        }
        return advanced;
      } catch (Throwable t) {
        state.recordException(t);
        state.end();
        throw t;
      }
    }

    @Override
    public Spliterator<Object> trySplit() {
      return null;
    }

    @Override
    public long estimateSize() {
      return delegate.estimateSize();
    }

    @Override
    public int characteristics() {
      return delegate.characteristics();
    }
  }
}
