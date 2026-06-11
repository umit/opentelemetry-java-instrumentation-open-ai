package io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.ChatService;
import com.openai.client.OpenAIClient;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class OpenAiInstrumentationModuleTest {
  @Test
  void registersUpstreamStyleInstrumentationNames() {
    OpenAiInstrumentationModule module = new OpenAiInstrumentationModule();

    assertEquals("umitunal-openai-java-4.0", module.instrumentationName());
    assertTrue(module.instrumentationNames().contains("umitunal-openai-java"));
    assertTrue(module.instrumentationNames().contains("openai-java-4.0"));
    assertEquals(1, module.typeInstrumentations().size());
    assertTrue(module.typeInstrumentations().get(0) instanceof OpenAiClientInstrumentation);
  }

  @Test
  void isDiscoverableThroughJavaServiceLoader() {
    boolean found = false;
    for (InstrumentationModule module : ServiceLoader.load(InstrumentationModule.class)) {
      if (module instanceof OpenAiInstrumentationModule) {
        found = true;
        break;
      }
    }

    assertTrue(found);
  }

  @Test
  void declaresAdviceHelperClassesForApplicationClassLoaderInjection() {
    List<String> helperClasses = new OpenAiInstrumentationModule().getAdditionalHelperClassNames();

    assertFalse(helperClasses.isEmpty());
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.GenAiJson"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0.OpenAiSingletons"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.OpenAiTelemetry"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy$OpenAiClientHandler"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy$ChatHandler"));
    assertTrue(helperClasses.contains(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client.OpenAiClientProxy$ChatCompletionsHandler"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry"));
    assertTrue(helperClasses.contains(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$ResponseCapturer"));
    assertTrue(helperClasses.contains(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$StreamResponseHandler"));
    assertTrue(helperClasses.contains(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$StreamState"));
    assertTrue(helperClasses.contains(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.streaming.StreamingTelemetry$InstrumentedSpliterator"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ChatCompletionTelemetry"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentCaptureMode"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentEmitter"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentAttributes"));
    assertTrue(helperClasses.contains(
        "io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ContentAttributes$ContentAttributesBuilder"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.OpenAiMetrics"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.RequestInfo"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ResponseTelemetry"));
    assertTrue(helperClasses.contains("io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil"));
  }

  @Test
  void adviceSingletonIsPublicForInjectedApplicationBytecode() throws NoSuchMethodException {
    assertTrue(Modifier.isPublic(OpenAiSingletons.class.getModifiers()));
    assertTrue(Modifier.isPublic(
        OpenAiSingletons.class.getDeclaredMethod("telemetry").getModifiers()));
  }

  @Test
  void buildAdviceWrapsReturnedClient() {
    OpenAIClient original = new FakeOpenAIClient();

    Object wrapped = OpenAiClientInstrumentation.BuildAdvice.onExit(original);

    assertNotSame(original, wrapped);
    assertTrue(wrapped instanceof OpenAIClient);
    assertTrue(Proxy.isProxyClass(wrapped.getClass()));
  }

  @Test
  void buildAdviceLeavesAlreadyWrappedClientAlone() {
    Object wrapped = OpenAiClientInstrumentation.BuildAdvice.onExit(new FakeOpenAIClient());

    assertSame(wrapped, OpenAiClientInstrumentation.BuildAdvice.onExit(wrapped));
  }

  static final class FakeOpenAIClient implements OpenAIClient {
    @Override
    public ChatService chat() {
      return null;
    }
  }
}
