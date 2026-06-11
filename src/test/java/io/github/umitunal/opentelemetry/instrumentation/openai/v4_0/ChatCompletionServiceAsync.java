package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0;

import java.util.concurrent.CompletableFuture;

public interface ChatCompletionServiceAsync {
  CompletableFuture<Object> create(Object params);
}
