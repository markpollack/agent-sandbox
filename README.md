# Agent Sandbox

Unified API for isolated command execution across multiple backends. Same interface for local processes, Docker containers, and E2B cloud microVMs.

## Modules

| Module | Description |
|--------|-------------|
| `agent-sandbox-core` | Core Sandbox API and LocalSandbox |
| `agent-sandbox-docker` | Docker container sandbox via Testcontainers |
| `agent-sandbox-e2b` | E2B cloud microVM sandbox |

## Maven Central

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-sandbox-core</artifactId>
    <version>0.9.2</version>
</dependency>
```

## Quick Example

```java
try (Sandbox sandbox = LocalSandbox.builder()
        .tempDirectory("test-")
        .build()) {

    ExecResult result = sandbox.exec(ExecSpec.of("mvn", "test"));

    if (result.success()) {
        System.out.println(result.stdout());
    }
}
```

## Documentation

Full API reference, all backends, file operations, and customizers:
https://lab.pollack.ai/projects/agent-sandbox

## Licensing

This project originated from earlier Apache-licensed work in the Spring AI Community.

Beginning with version 0.9.2, new development is licensed under the Business Source License 1.1 (BSL).

Historical Apache-licensed portions remain available under their original terms. See [LICENSE](LICENSE) and [LICENSE-APACHE.txt](LICENSE-APACHE.txt) for details.
