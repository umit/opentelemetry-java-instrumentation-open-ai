package io.github.umitunal.opentelemetry.instrumentation.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a GenAI tool/function implementation. The javaagent extension wraps the
 * annotated method in an {@code execute_tool <name>} span (gen_ai.operation.name=execute_tool,
 * gen_ai.tool.name=&lt;name&gt;). Any outbound HTTP call the tool makes nests under this span.
 *
 * <p>This is the Java analog of the OpenAI Agents SDK {@code @function_tool} decorator in Python.
 *
 * <pre>{@code
 * @FunctionTool("get_http_cat")
 * static String execute(ChatCompletionMessageFunctionToolCall toolCall) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FunctionTool {

  /** Tool name used in the span name and {@code gen_ai.tool.name}. Defaults to the method name. */
  String value() default "";
}
