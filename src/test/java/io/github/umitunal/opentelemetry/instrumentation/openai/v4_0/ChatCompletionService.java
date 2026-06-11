package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0;

public interface ChatCompletionService {
  Object create(Object params);

  Object createStreaming(Object params);
}
