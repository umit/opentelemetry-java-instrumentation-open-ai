package io.github.umitunal.opentelemetry.instrumentation.openai.v4_0.internal.telemetry;

import java.util.Locale;

public enum ContentCaptureMode {
  OFF(false, false),
  EVENT(true, false),
  SPAN_ATTRIBUTE(false, true),
  BOTH(true, true);

  public static final String PROPERTY = "otel.instrumentation.openai.content.capture.mode";
  public static final String EVENT_NAME = "gen_ai.client.inference.operation.details";

  private final boolean emitEvent;
  private final boolean emitSpanAttributes;

  ContentCaptureMode(boolean emitEvent, boolean emitSpanAttributes) {
    this.emitEvent = emitEvent;
    this.emitSpanAttributes = emitSpanAttributes;
  }

  public boolean emitsEvent() {
    return emitEvent;
  }

  public boolean emitsSpanAttributes() {
    return emitSpanAttributes;
  }

  public static ContentCaptureMode fromSystemProperty() {
    String configured = System.getProperty(PROPERTY, "off").trim().toLowerCase(Locale.ROOT);
    return switch (configured) {
      case "event" -> EVENT;
      case "span_attribute" -> SPAN_ATTRIBUTE;
      case "both" -> BOTH;
      default -> OFF;
    };
  }
}
