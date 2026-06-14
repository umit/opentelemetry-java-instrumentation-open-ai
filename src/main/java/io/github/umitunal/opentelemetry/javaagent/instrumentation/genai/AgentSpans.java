package io.github.umitunal.opentelemetry.javaagent.instrumentation.genai;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Opens and closes the GenAI agent-framework spans following the OpenTelemetry GenAI semantic
 * conventions (gen-ai-agent-spans):
 *
 * <ul>
 *   <li>{@code invoke_agent <agent>} — INTERNAL, gen_ai.operation.name=invoke_agent
 *   <li>{@code execute_tool <tool>} — INTERNAL, gen_ai.operation.name=execute_tool
 * </ul>
 *
 * <p>Each span is made current for the duration of the annotated method. Because OpenTelemetry
 * context is thread-local, the OpenAI client {@code chat} spans and the tool's outbound HTTP call —
 * created on the same thread by other instrumentations — automatically nest under these spans. This
 * supplies the agent boundary that a bespoke (frameworkless) agent loop otherwise lacks, mirroring
 * what the Python opentelemetry-instrumentation-openai-agents bridge does for the Agents SDK.
 */
public final class AgentSpans {

  private static final String INVOKE_AGENT =
      "io.github.umitunal.opentelemetry.instrumentation.annotations.InvokeAgent";
  private static final String FUNCTION_TOOL =
      "io.github.umitunal.opentelemetry.instrumentation.annotations.FunctionTool";

  private AgentSpans() {}

  private static Tracer tracer() {
    return GlobalOpenTelemetry.get().getTracer("io.github.umitunal.opentelemetry.genai", "0.1.0");
  }

  /** Opens an {@code invoke_agent <name>} span for an {@code @InvokeAgent} method. */
  public static Object startAgent(Method method) {
    String name = annotationValue(method, INVOKE_AGENT);
    if (name == null || name.isEmpty()) {
      name = method.getDeclaringClass().getSimpleName();
    }
    Span span =
        tracer()
            .spanBuilder("invoke_agent " + name)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("gen_ai.operation.name", "invoke_agent")
            .setAttribute("gen_ai.agent.name", name)
            .startSpan();
    return new Object[] {span, span.makeCurrent()};
  }

  /** Opens an {@code execute_tool <name>} span for a {@code @FunctionTool} method. */
  public static Object startTool(Method method) {
    String name = annotationValue(method, FUNCTION_TOOL);
    if (name == null || name.isEmpty()) {
      name = method.getName();
    }
    Span span =
        tracer()
            .spanBuilder("execute_tool " + name)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("gen_ai.operation.name", "execute_tool")
            .setAttribute("gen_ai.tool.name", name)
            .startSpan();
    return new Object[] {span, span.makeCurrent()};
  }

  /** Closes the scope and ends the span, recording the error if the annotated method threw. */
  public static void end(Object holder, Throwable error) {
    if (!(holder instanceof Object[] state)) {
      return;
    }
    Span span = (Span) state[0];
    Scope scope = (Scope) state[1];
    scope.close();
    if (error != null) {
      span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
      span.recordException(error);
    }
    span.end();
  }

  /**
   * Reads the {@code value()} of the annotation identified by {@code annotationFqn} by name, so it
   * works regardless of which classloader supplied the annotation type.
   */
  private static String annotationValue(Method method, String annotationFqn) {
    for (Annotation annotation : method.getAnnotations()) {
      if (annotation.annotationType().getName().equals(annotationFqn)) {
        try {
          Object value = annotation.annotationType().getMethod("value").invoke(annotation);
          return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
          return null;
        }
      }
    }
    return null;
  }
}
