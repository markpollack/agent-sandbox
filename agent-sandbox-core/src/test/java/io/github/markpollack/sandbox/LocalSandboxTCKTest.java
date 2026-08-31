/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.sandbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * TCK test implementation for LocalSandbox.
 *
 * <p>
 * Tests the LocalSandbox implementation against the standard sandbox TCK test suite.
 * These tests verify that LocalSandbox correctly implements all required Sandbox
 * behaviors.
 * </p>
 *
 * <p>
 * LocalSandbox executes commands on the host system with the specified working directory,
 * so these tests verify local execution behavior.
 * </p>
 */
class LocalSandboxTCKTest extends AbstractSandboxTCK {

	@TempDir
	private Path tempDir;

	@BeforeEach
	void setUp() {
		// Create LocalSandbox with temporary directory
		this.sandbox = new LocalSandbox(tempDir);
	}

}