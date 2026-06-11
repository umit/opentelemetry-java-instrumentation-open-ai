package io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0;

import static io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0.OpenAiSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class OpenAiClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.openai.client.okhttp.OpenAIOkHttpClient$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("build").and(returns(named("com.openai.client.OpenAIClient"))),
        getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static final class BuildAdvice {
    private BuildAdvice() {}

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Object onExit(@Advice.Return Object client) {
      return telemetry().wrap(client);
    }
  }
}
