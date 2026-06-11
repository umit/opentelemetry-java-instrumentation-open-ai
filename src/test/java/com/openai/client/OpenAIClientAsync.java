package com.openai.client;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.ChatServiceAsync;

public interface OpenAIClientAsync {
  ChatServiceAsync chat();

  default void close() {}
}
