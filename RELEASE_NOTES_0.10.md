# Agent Sandbox 0.10.0

Agent Sandbox 0.10.0 is a breaking pre-1.0 release. It removes the no-argument
`DockerSandbox()` constructor and the library's former default container image.
Callers must now select an image explicitly through an explicit-image constructor or
`DockerSandbox.builder().image(...)`. Pin an immutable image digest for reproducible
use, and apply your own provenance, patching, and vulnerability policy to that image.
Agent Sandbox publishes Java artifacts, not a container image.

The release retains the synchronous, TCK-tested `Sandbox` contract and its
`LocalSandbox`, explicit-image `DockerSandbox`, and E2B backends. `LocalSandbox` runs
directly on the host and provides no security isolation. `DockerSandbox` is a
convenient local backend for a caller-configured Docker daemon; it is not hardened
hostile-code or multi-tenant infrastructure.

Published artifacts are licensed under the Business Source License 1.1, include the
applicable license texts, and publish a CycloneDX 1.6 aggregate SBOM with the parent
artifact.

The published `agent-sandbox-docker` metadata now carries the managed
`commons-compress` 1.28.0 floor as a direct dependency. This makes a standalone Maven
consumer select the corrected version even without importing the Agent Sandbox parent
or an AgentWorks BOM, avoiding the two MEDIUM findings present in Testcontainers'
older transitive 1.24.0 selection. This correction applies to the Java dependency
closure; it does not describe or alter any caller-selected container image.

The actual Java runtime closure has three disclosed findings in Apache
HttpComponents classes shaded into the Testcontainers/docker-java transport: two
HIGH records and one MEDIUM record. Published upstream combinations examined do not
yet contain the fixed embedded versions, and ordinary dependency overrides cannot
replace the relocated classes. This is an accepted, monitored upstream dependency
exception for the trusted local Docker-daemon use case; the findings remain visible
and unsuppressed. The graph-derived SBOM does not expose these shaded components and
must not be read as evidence of a vulnerability-free dependency closure.
