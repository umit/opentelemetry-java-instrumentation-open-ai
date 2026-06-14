package io.github.umitunal.opentelemetry.instrumentation.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Verifies the {@link InvokeAgent} and {@link FunctionTool} annotations are usable by the
 * javaagent at runtime: retained, method-targeted, and their {@code value()} readable reflectively. */
class GenAiAnnotationsTest {

  @InvokeAgent("httpcat-agent")
  void annotatedAgent() {}

  @InvokeAgent
  void agentWithDefaultName() {}

  @FunctionTool("get_http_cat")
  void annotatedTool() {}

  @FunctionTool
  void toolWithDefaultName() {}

  @Test
  void invokeAgentIsRuntimeRetainedAndMethodTargeted() {
    assertEquals(
        RetentionPolicy.RUNTIME, InvokeAgent.class.getAnnotation(Retention.class).value());
    assertEquals(
        ElementType.METHOD, InvokeAgent.class.getAnnotation(Target.class).value()[0]);
  }

  @Test
  void functionToolIsRuntimeRetainedAndMethodTargeted() {
    assertEquals(
        RetentionPolicy.RUNTIME, FunctionTool.class.getAnnotation(Retention.class).value());
    assertEquals(
        ElementType.METHOD, FunctionTool.class.getAnnotation(Target.class).value()[0]);
  }

  @Test
  void invokeAgentValueIsReadableReflectively() throws NoSuchMethodException {
    Method method = GenAiAnnotationsTest.class.getDeclaredMethod("annotatedAgent");
    InvokeAgent annotation = method.getAnnotation(InvokeAgent.class);

    assertTrue(method.isAnnotationPresent(InvokeAgent.class));
    assertEquals("httpcat-agent", annotation.value());
  }

  @Test
  void functionToolValueIsReadableReflectively() throws NoSuchMethodException {
    Method method = GenAiAnnotationsTest.class.getDeclaredMethod("annotatedTool");
    FunctionTool annotation = method.getAnnotation(FunctionTool.class);

    assertTrue(method.isAnnotationPresent(FunctionTool.class));
    assertEquals("get_http_cat", annotation.value());
  }

  @Test
  void annotationsDefaultToEmptyValue() throws NoSuchMethodException {
    assertEquals(
        "",
        GenAiAnnotationsTest.class
            .getDeclaredMethod("agentWithDefaultName")
            .getAnnotation(InvokeAgent.class)
            .value());
    assertEquals(
        "",
        GenAiAnnotationsTest.class
            .getDeclaredMethod("toolWithDefaultName")
            .getAnnotation(FunctionTool.class)
            .value());
  }
}
