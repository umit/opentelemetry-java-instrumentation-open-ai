# OpenTelemetry Java Instrumentation for OpenAI

[![CI](https://github.com/umit/opentelemetry-java-instrumentation-open-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/umit/opentelemetry-java-instrumentation-open-ai/actions/workflows/ci.yml)

OpenTelemetry Java agent extension for instrumenting the OpenAI Java SDK 4.x.

The project follows the OpenTelemetry Java instrumentation split:

- `io.github.umitunal.opentelemetry.javaagent.instrumentation.openai.v4_0`: Java agent module and Byte Buddy advice.
- `io.github.umitunal.opentelemetry.instrumentation.openai.v4_0`: reusable telemetry helpers and proxy wrappers.

## Requirements

- Java 17 or later
- Maven 3.9 or later
- OpenTelemetry Java agent 2.28.1 or later
- OpenAI Java SDK 4.x

## What It Instruments

- `OpenAIOkHttpClient.Builder.build()` is wrapped automatically by the Java agent extension.
- `OpenAIClient.chat().completions().create(...)`
- `OpenAIClient.chat().completions().createStreaming(...)`
- `OpenAIClient.async().chat().completions().create(...)`

The instrumentation emits GenAI client spans, token usage metrics, operation duration metrics, streaming latency metrics, and optional prompt/output/tool content.

Telemetry follows the OpenTelemetry GenAI semantic conventions:

```text
https://opentelemetry.io/docs/specs/semconv/gen-ai/
```

## Build And Test

```bash
mvn -q test
mvn -q package
```

The extension jar is produced at:

```text
target/opentelemetry-java-instrumentation-open-ai-0.1.0.jar
```

## Installation

Download the OpenTelemetry Java agent from the official releases page:

```text
https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases
```

Build this extension locally:

```bash
mvn -q package
```

Then attach both the OpenTelemetry Java agent and this extension:

```bash
java \
  -javaagent:/path/to/opentelemetry-javaagent.jar \
  -Dotel.javaagent.extensions=/path/to/opentelemetry-java-instrumentation-open-ai-0.1.0.jar \
  -Dotel.instrumentation.openai-java.enabled=false \
  -Dotel.instrumentation.openai.content.capture.mode=event \
  -jar app.jar
```

`otel.instrumentation.openai-java.enabled=false` avoids double instrumentation when the upstream OpenAI Java instrumentation is also present in the Java agent.

## Content Capture

Content capture is disabled by default:

```text
otel.instrumentation.openai.content.capture.mode=off
```

Supported modes:

- `off`: do not capture prompts, outputs, or tool definitions.
- `event`: emit the OpenTelemetry GenAI event `gen_ai.client.inference.operation.details`.
- `span_attribute`: attach content fields directly to the chat span for Jaeger/Grafana debugging.
- `both`: emit the GenAI event and span attributes.

The standard OpenTelemetry GenAI model defines `gen_ai.input.messages`, `gen_ai.output.messages`, and `gen_ai.tool.definitions` as opt-in attributes on the `gen_ai.client.inference.operation.details` event. The `span_attribute` mode is a backend compatibility/debug mode, not the strict GenAI event model.

Content capture can include prompts, outputs, tool arguments, and tool results. Enable it only in environments where collecting this data is acceptable.

## Emitted Telemetry

- Span: `chat {gen_ai.request.model}` with `gen_ai.operation.name=chat` and `gen_ai.provider.name=openai`.
- Metrics: `gen_ai.client.token.usage`, `gen_ai.client.operation.duration`, `gen_ai.client.operation.time_to_first_chunk`, and `gen_ai.client.operation.time_per_output_chunk`.
- OpenAI attributes include `openai.api.type`, `openai.request.service_tier`, `openai.response.service_tier`, and `openai.response.system_fingerprint` when available.

## Example Trace

A chat completion with tool calling creates a client span similar to:

```text
chat gpt-4o-mini
|-- gen_ai.operation.name = chat
|-- gen_ai.provider.name = openai
|-- gen_ai.request.model = gpt-4o-mini
|-- gen_ai.request.max_tokens = 256
|-- gen_ai.request.temperature = 0.2
|-- gen_ai.request.top_p = 1
|-- gen_ai.response.id = chatcmpl_...
|-- gen_ai.response.model = gpt-4o-mini
|-- gen_ai.response.finish_reasons = ["tool_calls"]
|-- gen_ai.usage.cache_read.input_tokens = 256
|-- gen_ai.usage.input_tokens = 349
|-- gen_ai.usage.output_tokens = 47
|-- openai.api.type = chat_completions
`-- openai.response.system_fingerprint = fp_...
```

When content capture is enabled with `event`, the span also contains an opt-in event with request, response, and tool content:

```text
event: gen_ai.client.inference.operation.details
|-- gen_ai.input.messages = [
|     {
|       "role": "system",
|       "content": "You fetch http.cat images for HTTP status codes. Use the get_http_cat tool, then summarize the result for the user."
|     },
|     {
|       "role": "user",
|       "content": "Show me the cat for HTTP 404"
|     }
|   ]
|-- gen_ai.output.messages = [
|     {
|       "role": "assistant",
|       "content": "",
|       "tool_calls": [
|         {
|           "id": "call_...",
|           "type": "function",
|           "function": {
|             "name": "get_http_cat",
|             "arguments": "{\"status_code\": 404}"
|           }
|         }
|       ]
|     }
|   ]
`-- gen_ai.tool.definitions = [
      {
        "type": "function",
        "function": {
          "name": "get_http_cat",
          "description": "Fetch the http.cat image metadata for an HTTP status code.",
          "parameters": {
            "type": "object",
            "required": ["status_code"],
            "additionalProperties": false,
            "properties": {
              "status_code": {
                "type": "integer",
                "description": "The HTTP status code to look up, e.g. 200, 404, 500."
              }
            }
          }
        }
      }
    ]
```

When content capture is enabled with `span_attribute`, the same content fields are attached directly to the chat span for backend compatibility and debugging.

## Notes

- Production code avoids a compile-time dependency on the OpenAI SDK and uses reflection/proxy wrapping.
- Test fixtures under `src/test/java/com/openai/client` are minimal SDK shims used only by unit tests.
