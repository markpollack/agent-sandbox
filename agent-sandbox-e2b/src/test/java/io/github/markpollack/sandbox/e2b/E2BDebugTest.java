/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
