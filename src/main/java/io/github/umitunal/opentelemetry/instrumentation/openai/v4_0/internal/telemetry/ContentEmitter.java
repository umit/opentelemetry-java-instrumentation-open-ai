package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

@FunctionalInterface
public interface ContentEmitter {
  void emit(Context context, Attributes attributes);
}
