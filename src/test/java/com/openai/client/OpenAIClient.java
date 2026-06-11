package com.openai.client;

import io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.ChatService;

public interface OpenAIClient {
  default OpenAIClientAsync async() {
    return null;
  }

  ChatService chat();

  default void close() {}
}
