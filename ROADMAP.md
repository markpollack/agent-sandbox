# Agent Sandbox Roadmap

## Vision

A clean, minimal Java API for isolated command execution that supports multiple sandbox backends without leaking implementation-specific concerns into the core abstraction.

## Guiding Principles

1. **Interface Segregation**: Core `Sandbox` interface contains only universally-supported operations
2. **Construction-Time Configuration**: Implementation-specific features (security policies, API keys, resource limits) belong in builders, not the runtime interface
3. **Liskov Substitution**: All implementations must be substitutable without throwing `UnsupportedOperationException`
4. **Minimal Dependencies**: Avoid heavy frameworks (e.g., Project Reactor) in core module
5. **JDK 21 Baseline**: Leverage virtual threads for elegant, scalable async without framework complexity

## Implementation Tiers

### Tier 1: High Leverage, Low Regret (Implement First)

#### 1.1 `AnthropicSandbox` - Local OS-Level Policy Enforcement
- **What**: Lightweight sandboxing using OS primitives (macOS `sandbox-exec`, Linux `bubblewrap`)
- **Why**: No containers, no daemons, feels like a secure `ProcessBuilder`
- **Key Feature**: Security policy model (filesystem/network restrictions)
- **Reference**: [anthropic-experimental/sandbox-runtime](https://github.com/anthropic-experimental/sandbox-runtime)

#### 1.2 `E2BSandbox` - Remote MicroVM Sandbox
- **What**: Cloud-based Firecracker microVM execution via REST API
- **Why**: Strong isolation, remote execution for CI/evals, OSS core
- **Key Feature**: Reconnectable sessions, full file operations
- **New (2025)**: [Docker + E2B partnership](https://www.docker.com/blog/docker-e2b-building-the-future-of-trusted-ai/) adds native MCP support with 200+ tools from Docker MCP Catalog
- **Reference**: [e2b-dev/E2B](https://github.com/e2b-dev/E2B)

**E2BSandbox MCP Integration** (builder-based, not in core interface):
```java
Sandbox e2b = E2BSandbox.builder()
    .apiKey(System.getenv("E2B_API_KEY"))
    .mcp("github", Map.of("githubPersonalAccessToken", token))
    .mcp("notion", Map.of("internalIntegrationToken", notionToken))
    .build();

// Access MCP gateway for mcp-java integration
String mcpUrl = ((E2BSandbox) e2b).getMcpUrl();
String mcpToken = ((E2BSandbox) e2b).getMcpToken();
```

### Tier 2: Implement If Demand Emerges

#### 2.1 `DaytonaSandbox` - Container-First Agent Infrastructure
- **What**: Fast container sandboxes with git-first workflow
- **Why**: Good for full development environment scenarios
- **Key Feature**: Sessions, volumes, LSP integration
- **Reference**: [daytonaio/sdk](https://github.com/daytonaio/sdk)

#### 2.2 `MicrosandboxSandbox` - Self-Hosted MicroVM
- **What**: Self-hosted alternative to E2B using libkrun microVMs
- **Why**: Full control, no vendor lock-in
- **Reference**: [zerocore-ai/microsandbox](https://github.com/zerocore-ai/microsandbox)

### Tier 3: Do NOT Target Directly

- Cloudflare Workers, Fly.io, Vercel Sandboxes, Replit, CodeSandbox, StackBlitz
- These are deployment platforms, not agent execution primitives

## API Design

### Core Interface (Universal)

```java
public interface Sandbox extends AutoCloseable {
    ExecResult exec(ExecSpec spec);
    SandboxFiles files();
    Path workDir();
    boolean isClosed();
}
```

### Implementation-Specific Configuration (Builder Pattern)

```java
// Local sandbox (no isolation) - existing
Sandbox local = LocalSandbox.builder()
    .workingDirectory(path)
    .build();

// Docker sandbox (container isolation) - existing
Sandbox docker = DockerSandbox.builder()
    .image("python:3.11")
    .build();

// Anthropic-style (OS-level policy enforcement) - NEW
Sandbox secure = AnthropicSandbox.builder()
    .policy(SandboxPolicy.builder()
        .filesystem(fs -> fs
            .denyRead("~/.ssh", "~/.aws")
            .allowWrite("/workspace"))
        .network(net -> net
            .allowHosts("github.com", "*.npmjs.org"))
        .build())
    .build();

// E2B (remote cloud sandbox) - NEW
Sandbox e2b = E2BSandbox.builder()
    .apiKey(System.getenv("E2B_API_KEY"))
    .template("base")
    .timeout(Duration.ofMinutes(10))
    .build();
```

### Why Policy is NOT in the Core Interface

| Principle | Violation if Policy in Interface |
|-----------|----------------------------------|
| **ISP** | E2B/Docker forced to depend on policy methods they don't use |
| **LSP** | `E2BSandbox.applyPolicy()` would throw or no-op |
| **OCP** | Adding Anthropic features modifies core interface |

**Solution**: Policy is a construction-time concern, configured via `AnthropicSandbox.Builder`.

## Validated API Extensions

Based on analysis of E2B, Daytona, and Microsandbox SDKs:

### P0: Must Have

| Extension | Rationale | All SDKs Support |
|-----------|-----------|------------------|
| Separate `stdout`/`stderr` in `ExecResult` | Universal pattern | Yes |

```java
// Current
record ExecResult(int exitCode, String mergedLog, Duration duration)

// Proposed
record ExecResult(int exitCode, String stdout, String stderr, Duration duration) {
    public String mergedLog() { return stdout + stderr; }  // Backwards compatible
}
```

### P1: High Value

| Extension | Rationale |
|-----------|-----------|
| `SandboxFiles.list(path)` | E2B + Daytona support |
| `SandboxFiles.delete(path)` | E2B + Daytona support |
| `Sandbox.execAsync()` → `CommandHandle` | Background execution with streaming |

### P2: Medium Value

| Extension | Rationale |
|-----------|-----------|
| `RemoteSandbox.connect(id)` | Reconnect to existing E2B/Daytona sandbox |
| `SandboxPolicy` | Anthropic-style local sandboxing only |

### P3: Nice to Have

| Extension | Supporting SDKs |
|-----------|-----------------|
| Resource metrics | E2B, Microsandbox |
| File watch | E2B only |
| Git operations | Daytona only |

## Async Strategy

### Baseline: JDK 21 with Virtual Threads

With JDK 21 as our baseline, we get elegant async without framework complexity:

```java
// Async execution with CompletableFuture
public interface Sandbox {
    ExecResult exec(ExecSpec spec);                         // Blocking
    CompletableFuture<ExecResult> execAsync(ExecSpec spec); // Non-blocking
}

// Streaming via simple callbacks
public interface CommandHandle {
    String id();
    CompletableFuture<ExecResult> result();
    void kill();
    void onStdout(Consumer<String> callback);
    void onStderr(Consumer<String> callback);
}
```

### Why Virtual Threads Instead of Reactor?

| Concern | Reactor | JDK 21 Virtual Threads |
|---------|---------|------------------------|
| Dependency | reactor-core (~2MB) | None (JDK only) |
| Learning curve | Steep (operators, backpressure) | None (blocking code) |
| Debugging | Complex stack traces | Simple, familiar |
| Code style | Reactive chains | Imperative, readable |
| Spring integration | Native | Easy to wrap in Mono |

### Implementation Pattern

Virtual threads make blocking I/O cheap and scalable:

```java
public class CommandHandleImpl implements CommandHandle {
    public CommandHandleImpl(Process process) {
        // Virtual threads for stream pumping - cheap and simple
        Thread.startVirtualThread(() -> pumpStream(process.getInputStream(), stdoutCallback));
        Thread.startVirtualThread(() -> pumpStream(process.getErrorStream(), stderrCallback));
        Thread.startVirtualThread(this::awaitCompletion);
    }

    private void pumpStream(InputStream is, Consumer<String> callback) {
        try (var reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                callback.accept(line);  // Blocking is fine - virtual thread yields
            }
        }
    }
}
```

### Scalability

| Scenario | Platform Threads | Virtual Threads |
|----------|------------------|-----------------|
| 100 sandboxes (stdout+stderr) | 200 threads (~200MB) | 200 virtual (~200KB) |
| 1000 sandboxes | Thread pool exhaustion | Still lightweight |
| Blocking I/O | Thread pinned | Thread yields, reused |

## Module Structure

```
agent-sandbox/
├── agent-sandbox-core/        # Core API + LocalSandbox
├── agent-sandbox-docker/      # DockerSandbox (Testcontainers)
├── agent-sandbox-anthropic/   # AnthropicSandbox (planned, not yet built)
├── agent-sandbox-e2b/         # E2BSandbox
└── agent-sandbox-bom/         # BOM (planned; versions are managed by agentworks-bom today)
```

## Implementation Phases

### Phase 1: Core API Enhancements ✅
- [x] Separate `stdout`/`stderr` in `ExecResult`
- [x] Add `list()` and `delete()` to `SandboxFiles`
- [x] Add `FileEntry` record for directory listing results
- [x] Update TCK tests (9 new tests for list/delete operations)

### Phase 2: Async Support
- [ ] Add `execAsync()` returning `CompletableFuture<ExecResult>`
- [ ] Add `CommandHandle` for background processes with streaming callbacks
- [ ] Ensure thread-safety

### Phase 3: AnthropicSandbox
- [ ] Create `agent-sandbox-anthropic` module
- [ ] Implement `SandboxPolicy` builder
- [ ] Wrap Anthropic `srt` CLI or native port
- [ ] Platform detection (macOS/Linux)

### Phase 4: E2BSandbox ✅
- [x] Create `agent-sandbox-e2b` module
- [x] Implement REST client for E2B API (sandbox lifecycle)
- [x] Implement envd client for command execution
- [x] Implement E2BSandboxFiles for file operations
- [x] Support sandbox reconnection via `E2BSandbox.connect(sandboxId)`
- [x] Handle authentication (API key via builder or `E2B_API_KEY` env var)
- [x] Integration testing (`E2BSandboxIT` runs the full TCK against the live service)

## References

- [Anthropic sandbox-runtime](https://github.com/anthropic-experimental/sandbox-runtime)
- [E2B SDK](https://github.com/e2b-dev/E2B)
- [Daytona SDK](https://github.com/daytonaio/sdk)
- [Microsandbox](https://github.com/zerocore-ai/microsandbox)
- [Agent Sandbox documentation](https://lab.pollack.ai/projects/agent-sandbox)
