/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.sandbox.docker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import io.github.markpollack.sandbox.AbstractSandboxTCK;

import java.util.List;

/**
 * TCK test implementation for DockerSandbox.
 *
 * <p>
 * Tests the DockerSandbox implementation against the standard sandbox TCK test suite.
 * These tests verify that DockerSandbox correctly implements all required Sandbox
 * behaviors using Docker containers for isolation.
 * </p>
 *
 * <p>
 * Run with: mvn test -Dtest=DockerSandboxTCKTest -Dsandbox.infrastructure.test=true
 * </p>
 *
 * <p>
 * Requires a Docker daemon. Runs against a minimal POSIX test fixture pinned by
 * digest; see {@link DockerTestImages}.
 * </p>
 */
@EnabledIfSystemProperty(named = "sandbox.infrastructure.test", matches = "true")
class DockerSandboxTCKTest extends AbstractSandboxTCK {

	@BeforeEach
	void setUp() {
		// The caller always selects the image; this backend has no default.
		this.sandbox = new DockerSandbox(DockerTestImages.MINIMAL_POSIX, List.of());
	}

}
