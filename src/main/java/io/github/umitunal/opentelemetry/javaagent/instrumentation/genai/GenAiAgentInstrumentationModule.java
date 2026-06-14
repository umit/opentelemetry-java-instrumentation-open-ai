package io.github.umitunal.opentelemetry.javaagent.instrumentation.genai;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Annotation-driven GenAI agent/framework instrumentation. Emits {@code invoke_agent} and
 * {@code execute_tool} spans for methods annotated with
 * {@code io.github.umitunal.opentelemetry.instrumentation.annotations.InvokeAgent} and
 * {@code ...FunctionTool}.
 *
 * <p>It is intentionally generic — it knows nothing about any specific application. This is the Java
 * analog of the Python opentelemetry-instrumentation-openai-agents package: a dedicated
 * agent-framework instrumentation, separate from the OpenAI client instrumentation, that supplies
 * the {@code invoke_agent} root span so the client {@code chat} spans collapse into one trace. It
 * only activates when the annotations are on the classpath.
 */
@AutoService(InstrumentationModule.class)
public final class GenAiAgentInstrumentationModule extends InstrumentationModule {

  public GenAiAgentInstrumentationModule() {
    super("genai-agent-annotations");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(InvokeAgentInstrumentation.ANNOTATION);
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return List.of(new InvokeAgentInstrumentation(), new FunctionToolInstrumentation());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of("io.github.umitunal.opentelemetry.javaagent.instrumentation.genai.AgentSpans");
  }
}
