package io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public final class OpenAiInstrumentationModule extends InstrumentationModule {
  public OpenAiInstrumentationModule() {
    super("umitunal-openai-java-4.0", "umitunal-openai-java", "openai-java-4.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.openai.client.OpenAIClient");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return List.of(new OpenAiClientInstrumentation());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.GenAiJson",
        "io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0.OpenAiSingletons",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.OpenAiTelemetry",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy$OpenAiClientHandler",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy$ChatHandler",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy$ChatCompletionsHandler",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$ResponseCapturer",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$StreamResponseHandler",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$StreamState",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$InstrumentedSpliterator",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ChatCompletionTelemetry",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentCaptureMode",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentEmitter",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentAttributes",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentAttributes$ContentAttributesBuilder",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.OpenAiMetrics",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.RequestInfo",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ResponseTelemetry",
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil");
  }
}
