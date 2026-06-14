package io.github.umitunal.opentelemetry.javaagent.instrumentation.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.umitunal.opentelemetry.instrumentation.annotations.FunctionTool;
import io.github.umitunal.opentelemetry.instrumentation.annotations.InvokeAgent;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import java.util.ServiceLoader;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

class GenAiAgentInstrumentationModuleTest {

  static final class AgentSample {
    @InvokeAgent("sample-agent")
    void run() {}
  }

  static final class ToolSample {
    @FunctionTool("sample-tool")
    void execute() {}
  }

  static final class PlainSample {
    void nothing() {}
  }

  @Test
  void registersInstrumentationName() {
    assertEquals("genai-agent-annotations", new GenAiAgentInstrumentationModule().instrumentationName());
  }

  @Test
  void declaresInvokeAgentAndFunctionToolInstrumentations() {
    List<TypeInstrumentation> instrumentations =
        new GenAiAgentInstrumentationModule().typeInstrumentations();

    assertEquals(2, instrumentations.size());
    assertTrue(instrumentations.get(0) instanceof InvokeAgentInstrumentation);
    assertTrue(instrumentations.get(1) instanceof FunctionToolInstrumentation);
  }

  @Test
  void declaresAgentSpansHelperForApplicationClassLoaderInjection() {
    assertTrue(
        new GenAiAgentInstrumentationModule()
            .getAdditionalHelperClassNames()
            .contains("io.github.umitunal.opentelemetry.javaagent.instrumentation.genai.AgentSpans"));
  }

  @Test
  void isDiscoverableThroughJavaServiceLoader() {
    boolean found = false;
    for (InstrumentationModule module : ServiceLoader.load(InstrumentationModule.class)) {
      if (module instanceof GenAiAgentInstrumentationModule) {
        found = true;
        break;
      }
    }

    assertTrue(found);
  }

  @Test
  void classLoaderMatcherMatchesWhenAnnotationsArePresent() {
    assertTrue(
        new GenAiAgentInstrumentationModule()
            .classLoaderMatcher()
            .matches(getClass().getClassLoader()));
  }

  @Test
  void invokeAgentTypeMatcherSelectsOnlyAgentAnnotatedTypes() {
    var typeMatcher = new InvokeAgentInstrumentation().typeMatcher();

    assertTrue(typeMatcher.matches(new TypeDescription.ForLoadedType(AgentSample.class)));
    assertFalse(typeMatcher.matches(new TypeDescription.ForLoadedType(ToolSample.class)));
    assertFalse(typeMatcher.matches(new TypeDescription.ForLoadedType(PlainSample.class)));
  }

  @Test
  void functionToolTypeMatcherSelectsOnlyToolAnnotatedTypes() {
    var typeMatcher = new FunctionToolInstrumentation().typeMatcher();

    assertTrue(typeMatcher.matches(new TypeDescription.ForLoadedType(ToolSample.class)));
    assertFalse(typeMatcher.matches(new TypeDescription.ForLoadedType(AgentSample.class)));
    assertFalse(typeMatcher.matches(new TypeDescription.ForLoadedType(PlainSample.class)));
  }
}
