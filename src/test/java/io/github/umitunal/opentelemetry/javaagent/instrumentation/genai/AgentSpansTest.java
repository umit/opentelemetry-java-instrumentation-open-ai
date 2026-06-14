package io.github.umitunal.opentelemetry.javaagent.instrumentation.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.umitunal.opentelemetry.instrumentation.annotations.FunctionTool;
import io.github.umitunal.opentelemetry.instrumentation.annotations.InvokeAgent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentSpansTest {

  private static final AttributeKey<String> OPERATION_NAME =
      AttributeKey.stringKey("gen_ai.operation.name");
  private static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
  private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");

  private final InMemorySpanExporter spans = InMemorySpanExporter.create();

  @InvokeAgent("httpcat-agent")
  static void agentRun() {}

  @InvokeAgent
  static void agentWithoutName() {}

  @FunctionTool("get_http_cat")
  static void toolExecute() {}

  @FunctionTool
  static void toolWithoutName() {}

  @BeforeEach
  void registerGlobalSdk() {
    GlobalOpenTelemetry.resetForTest();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(spans))
                    .build())
            .build();
    GlobalOpenTelemetry.set(sdk);
  }

  @AfterEach
  void resetGlobal() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void invokeAgentSpanUsesAnnotationName() throws NoSuchMethodException {
    AgentSpans.end(AgentSpans.startAgent(method("agentRun")), null);

    SpanData span = single();
    assertEquals("invoke_agent httpcat-agent", span.getName());
    assertEquals(SpanKind.INTERNAL, span.getKind());
    assertEquals("invoke_agent", span.getAttributes().get(OPERATION_NAME));
    assertEquals("httpcat-agent", span.getAttributes().get(AGENT_NAME));
    assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
  }

  @Test
  void executeToolSpanUsesAnnotationName() throws NoSuchMethodException {
    AgentSpans.end(AgentSpans.startTool(method("toolExecute")), null);

    SpanData span = single();
    assertEquals("execute_tool get_http_cat", span.getName());
    assertEquals(SpanKind.INTERNAL, span.getKind());
    assertEquals("execute_tool", span.getAttributes().get(OPERATION_NAME));
    assertEquals("get_http_cat", span.getAttributes().get(TOOL_NAME));
  }

  @Test
  void invokeAgentFallsBackToDeclaringClassName() throws NoSuchMethodException {
    AgentSpans.end(AgentSpans.startAgent(method("agentWithoutName")), null);

    SpanData span = single();
    assertEquals("invoke_agent AgentSpansTest", span.getName());
    assertEquals("AgentSpansTest", span.getAttributes().get(AGENT_NAME));
  }

  @Test
  void executeToolFallsBackToMethodName() throws NoSuchMethodException {
    AgentSpans.end(AgentSpans.startTool(method("toolWithoutName")), null);

    SpanData span = single();
    assertEquals("execute_tool toolWithoutName", span.getName());
    assertEquals("toolWithoutName", span.getAttributes().get(TOOL_NAME));
  }

  @Test
  void toolSpanNestsUnderAgentSpanOnSameThread() throws NoSuchMethodException {
    Object agent = AgentSpans.startAgent(method("agentRun"));
    Object tool = AgentSpans.startTool(method("toolExecute"));
    AgentSpans.end(tool, null);
    AgentSpans.end(agent, null);

    SpanData agentSpan = byName("invoke_agent httpcat-agent");
    SpanData toolSpan = byName("execute_tool get_http_cat");
    assertEquals(agentSpan.getSpanContext().getTraceId(), toolSpan.getSpanContext().getTraceId());
    assertEquals(agentSpan.getSpanContext().getSpanId(), toolSpan.getParentSpanContext().getSpanId());
  }

  @Test
  void errorIsRecordedOnSpan() throws NoSuchMethodException {
    AgentSpans.end(AgentSpans.startAgent(method("agentRun")), new IllegalStateException("boom"));

    SpanData span = single();
    assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
    assertTrue(span.getEvents().stream().anyMatch(event -> event.getName().equals("exception")));
  }

  @Test
  void endIgnoresNullHolder() {
    AgentSpans.end(null, null);

    assertTrue(spans.getFinishedSpanItems().isEmpty());
  }

  @Test
  void endIgnoresUnexpectedHolderType() {
    AgentSpans.end("not a span holder", new IllegalStateException("boom"));

    assertTrue(spans.getFinishedSpanItems().isEmpty());
  }

  @Test
  void invokeAgentAdviceOpensAndClosesSpan() throws NoSuchMethodException {
    Object scope =
        InvokeAgentInstrumentation.InvokeAgentAdvice.onEnter(method("agentRun"));
    InvokeAgentInstrumentation.InvokeAgentAdvice.onExit(scope, null);

    assertEquals("invoke_agent httpcat-agent", single().getName());
  }

  @Test
  void functionToolAdviceOpensAndClosesSpan() throws NoSuchMethodException {
    Object scope =
        FunctionToolInstrumentation.FunctionToolAdvice.onEnter(method("toolExecute"));
    FunctionToolInstrumentation.FunctionToolAdvice.onExit(scope, null);

    assertEquals("execute_tool get_http_cat", single().getName());
  }

  private Method method(String name) throws NoSuchMethodException {
    return AgentSpansTest.class.getDeclaredMethod(name);
  }

  private SpanData single() {
    List<SpanData> finished = spans.getFinishedSpanItems();
    assertEquals(1, finished.size());
    return finished.get(0);
  }

  private SpanData byName(String name) {
    return spans.getFinishedSpanItems().stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no span named " + name));
  }
}
