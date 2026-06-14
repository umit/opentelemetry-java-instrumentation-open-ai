package io.github.umitunal.opentelemetry.javaagent.instrumentation.genai;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Wraps every {@code @FunctionTool}-annotated method in an {@code execute_tool} span. */
final class FunctionToolInstrumentation implements TypeInstrumentation {

  static final String ANNOTATION =
      "io.github.umitunal.opentelemetry.instrumentation.annotations.FunctionTool";

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(isAnnotatedWith(named(ANNOTATION)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isAnnotatedWith(named(ANNOTATION)), getClass().getName() + "$FunctionToolAdvice");
  }

  @SuppressWarnings("unused")
  public static final class FunctionToolAdvice {
    private FunctionToolAdvice() {}

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object onEnter(@Advice.Origin Method method) {
      return AgentSpans.startTool(method);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter Object scope, @Advice.Thrown Throwable error) {
      AgentSpans.end(scope, error);
    }
  }
}
