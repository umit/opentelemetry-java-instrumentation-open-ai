package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.client;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry.ChatCompletionTelemetry;
import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.util.ReflectionUtil;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public final class OpenAiClientProxy {
  private OpenAiClientProxy() {}

  public static Object wrap(Object client, ChatCompletionTelemetry telemetry) {
    return ReflectionUtil.proxy(
        client,
        "com.openai.client.OpenAIClient",
        new OpenAiClientHandler(client, telemetry));
  }

  private static Object wrapAsync(Object client, ChatCompletionTelemetry telemetry) {
    return ReflectionUtil.proxy(
        client,
        "com.openai.client.OpenAIClientAsync",
        new OpenAiClientHandler(client, telemetry));
  }

  private static Object wrapChat(Object chat, ChatCompletionTelemetry telemetry) {
    return ReflectionUtil.proxy(chat, null, new ChatHandler(chat, telemetry));
  }

  private static Object wrapCompletions(Object completions, ChatCompletionTelemetry telemetry) {
    return ReflectionUtil.proxy(completions, null, new ChatCompletionsHandler(completions, telemetry));
  }

  private static final class OpenAiClientHandler implements InvocationHandler {
    private final Object delegate;
    private final ChatCompletionTelemetry telemetry;

    private OpenAiClientHandler(Object delegate, ChatCompletionTelemetry telemetry) {
      this.delegate = delegate;
      this.telemetry = telemetry;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (ReflectionUtil.isObjectMethod(method)) {
        return ReflectionUtil.invoke(delegate, method, args);
      }
      Object result = ReflectionUtil.invoke(delegate, method, args);
      if (args != null && args.length != 0) {
        return result;
      }
      if (method.getName().equals("async")) {
        return wrapAsync(result, telemetry);
      }
      if (method.getName().equals("chat")) {
        return wrapChat(result, telemetry);
      }
      return result;
    }
  }

  private static final class ChatHandler implements InvocationHandler {
    private final Object delegate;
    private final ChatCompletionTelemetry telemetry;

    private ChatHandler(Object delegate, ChatCompletionTelemetry telemetry) {
      this.delegate = delegate;
      this.telemetry = telemetry;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (ReflectionUtil.isObjectMethod(method)) {
        return ReflectionUtil.invoke(delegate, method, args);
      }
      Object result = ReflectionUtil.invoke(delegate, method, args);
      return method.getName().equals("completions") && (args == null || args.length == 0)
          ? wrapCompletions(result, telemetry)
          : result;
    }
  }

  private static final class ChatCompletionsHandler implements InvocationHandler {
    private final Object delegate;
    private final ChatCompletionTelemetry telemetry;

    private ChatCompletionsHandler(Object delegate, ChatCompletionTelemetry telemetry) {
      this.delegate = delegate;
      this.telemetry = telemetry;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (ReflectionUtil.isObjectMethod(method) || args == null || args.length == 0) {
        return ReflectionUtil.invoke(delegate, method, args);
      }
      if (method.getName().equals("create")) {
        if (java.util.concurrent.CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
          return telemetry.createAsync(delegate, method, args);
        }
        return telemetry.create(delegate, method, args);
      }
      if (method.getName().equals("createStreaming")) {
        return telemetry.createStreaming(delegate, method, args);
      }
      return ReflectionUtil.invoke(delegate, method, args);
    }
  }
}
