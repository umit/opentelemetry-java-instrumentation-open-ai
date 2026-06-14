package io.github.umitunal.opentelemetry.instrumentation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the entry point of a GenAI agent run. The javaagent extension wraps the
 * annotated method in an {@code invoke_agent <name>} span (gen_ai.operation.name=invoke_agent,
 * gen_ai.agent.name=&lt;name&gt;) and makes it current, so the OpenAI {@code chat} spans and any
 * {@link FunctionTool}-annotated tool calls executed during the run nest under it as one trace.
 *
 * <p>This is the Java analog of the OpenAI Agents SDK {@code Agent}/{@code Runner} boundary in
 * Python — the annotation supplies the agent boundary that a bespoke agent loop otherwise lacks.
 *
 * <pre>{@code
 * @InvokeAgent("httpcat-agent")
 * static String run(OpenAIClient client, String userRequest) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeAgent {

  /** Agent name used in the span name and {@code gen_ai.agent.name}. Defaults to the class name. */
  String value() default "";
}
