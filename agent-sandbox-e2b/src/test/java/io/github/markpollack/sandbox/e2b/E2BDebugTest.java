/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.sandbox.e2b;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;

/**
 * Debug test for E2B sandbox to understand response format.
 */
@EnabledIfEnvironmentVariable(named = "E2B_API_KEY", matches = ".+")
class E2BDebugTest {

	private E2BSandbox sandbox;

	@BeforeEach
	void setUp() {
		sandbox = E2BSandbox.builder().timeout(Duration.ofMinutes(2)).build();
		System.out.println("Created sandbox: " + sandbox.sandboxId());
	}

	@AfterEach
	void tearDown() {
		if (sandbox != null && !sandbox.isClosed()) {
			sandbox.close();
		}
	}

	@Test
	void testEchoCommand() {
		ExecSpec spec = ExecSpec.builder().command("echo", "hello").timeout(Duration.ofSeconds(30)).build();

		ExecResult result = sandbox.exec(spec);

		System.out.println("=== STDOUT RESULT ===");
		System.out.println("Exit code: " + result.exitCode());
		System.out.println("Stdout: [" + result.stdout() + "]");
		System.out.println("Stderr: [" + result.stderr() + "]");
		System.out.println("Success: " + result.success());
	}

	@Test
	void testStderrCommand() {
		// Test stderr capture
		ExecSpec spec = ExecSpec.builder()
			.shellCommand("echo 'error message' >&2")
			.timeout(Duration.ofSeconds(30))
			.build();

		ExecResult result = sandbox.exec(spec);

		System.out.println("=== STDERR RESULT ===");
		System.out.println("Exit code: " + result.exitCode());
		System.out.println("Stdout: [" + result.stdout() + "]");
		System.out.println("Stderr: [" + result.stderr() + "]");
		System.out.println("Success: " + result.success());
	}

}
