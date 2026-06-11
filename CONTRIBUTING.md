# Contributing

Thanks for considering a contribution.

## Development

Use Java 17 and Maven.

```bash
mvn -q test
mvn -q package
```

Keep instrumentation focused on the OpenAI Java SDK. Demo- or application-specific instrumentation belongs in a separate example project.

## Pull Requests

- Describe the OpenAI SDK surface or semantic convention behavior being changed.
- Add or update tests for new spans, attributes, metrics, or content-capture modes.
- Keep sensitive content capture opt-in.
- Run `mvn -q test` before opening a PR.

## Semantic Conventions

Prefer OpenTelemetry GenAI semantic conventions from `opentelemetry-semconv-incubating`. If behavior intentionally differs for backend compatibility, document it in `README.md`.
