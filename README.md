# Agent Sandbox

One Java API for running commands in an isolated workspace, with interchangeable
backends. An agent, an evaluation harness, or a build runner writes against the
`Sandbox` interface once; whether that command runs as a local process, inside a
Docker container, or in a remote Firecracker microVM becomes a construction-time
choice rather than a rewrite.

**Documentation: [lab.pollack.ai/projects/agent-sandbox](https://lab.pollack.ai/projects/agent-sandbox)** —
backends, the core API, file operations and customizers.
Release notes: [What's New](https://lab.pollack.ai/docs/agent-sandbox/whats-new).

## Concepts

- **`Sandbox`** — the backend-neutral surface: `exec`, `files()`, `workDir()`, and
  `close()`. Everything a backend cannot offer everywhere stays off this interface.
- **`ExecSpec` / `ExecResult`** — an immutable command specification (argument vector,
  environment overrides, timeout) and its outcome (exit code, stdout, stderr, duration).
  An `ExecSpec` is an argument vector on every backend: arguments are never split or
  expanded by a shell.
- **`SandboxFiles`** — fluent file operations against the sandbox workspace, chained
  back into the sandbox with `and()`.
- **`ExecSpecCustomizer`** — construction-time interception of each spec before it runs.
- **`AbstractSandboxTCK`** — the compatibility kit every backend passes, published in
  the `agent-sandbox-core` test-jar so an out-of-tree backend can run it too.

## Modules

| Module | Backend | Isolation | Notable dependencies |
|---|---|---|---|
| `agent-sandbox-core` | `LocalSandbox` | none — runs on the host | zt-exec, slf4j |
| `agent-sandbox-docker` | `DockerSandbox` | container | testcontainers |
| `agent-sandbox-e2b` | `E2BSandbox` | remote microVM | jackson, awaitility |

`LocalSandbox` deliberately provides no isolation and says so at runtime; it is for
trusted code and for development. Use `DockerSandbox` or `E2BSandbox` when the command
is not trusted.

## Maven

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>agent-sandbox-core</artifactId>
    <version>0.9.3</version>
</dependency>
```

Add `agent-sandbox-docker` or `agent-sandbox-e2b` for those backends; each brings
`agent-sandbox-core` transitively.

## Example

```java
try (Sandbox sandbox = LocalSandbox.builder()
        .tempDirectory("build-")
        .build()) {

    ExecResult result = sandbox.files()
        .create("pom.xml", pomContent)
        .create("src/main/java/App.java", code)
        .and()
        .exec(ExecSpec.of("mvn", "-q", "compile"));

    if (result.success()) {
        System.out.println(result.stdout());
    }
}
```

## Build

```bash
./mvnw clean verify
```

That runs the unit tests and the `LocalSandbox` TCK. The other two backends need
infrastructure and gate themselves off when it is absent:

```bash
./mvnw -pl agent-sandbox-docker -Dsandbox.infrastructure.test=true test  # needs a Docker daemon
./mvnw -pl agent-sandbox-e2b verify                                      # needs E2B_API_KEY
```

A local dependency CVE scan is available and is deliberately not part of ordinary CI:

```bash
./mvnw -Powasp verify        # OWASP dependency-check, fails on CVSS >= 7.0
./scripts/security-scan.sh   # Trivy, HIGH/CRITICAL
```

## Maturity

Pre-1.0 and versioned accordingly: the API may change between minor versions.
`LocalSandbox` and `DockerSandbox` are exercised by the full TCK; `E2BSandbox` passes
the same TCK against the live E2B service. `agent-sandbox-docker` requires Docker
Engine with API version 1.44 or newer.

## Licensing

This project originated from earlier Apache-licensed work in the Spring AI Community.
Its last Apache License 2.0 release was `org.springaicommunity:agent-sandbox-*` 0.9.1.
Beginning with 0.9.2 — the first release under the `io.github.markpollack` coordinates —
new development is licensed under the Business Source License 1.1.

The 0.9.0 and 0.9.1 artifacts published under Apache 2.0 remain available under their
original terms; nothing already released has been relicensed.
See [Business Source License 1.1](LICENSE) and [LICENSE-APACHE.txt](LICENSE-APACHE.txt).
