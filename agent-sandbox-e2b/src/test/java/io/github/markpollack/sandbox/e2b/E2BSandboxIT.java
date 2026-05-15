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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.sandbox.AbstractSandboxTCK;

/**
 * Integration tests for E2BSandbox implementation.
 *
 * <p>
 * These tests follow the standard TCK pattern: one sandbox per test for accurate
 * lifecycle testing. A small delay after cleanup ensures E2B has time to process sandbox
 * termination before the next test creates a new one.
 * </p>
 *
 * <p>
 * Requires E2B_API_KEY environment variable. Run with:
 * {@code mvn verify -pl spring-ai-sandbox-e2b}
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
@TestInstance(Lifecycle.PER_METHOD)
@EnabledIfEnvironmentVariable(named = "E2B_API_KEY", matches = ".+")
class E2BSandboxIT extends AbstractSandboxTCK {

	private static final Logger logger = LoggerFactory.getLogger(E2BSandboxIT.class);

	/**
	 * Delay after sandbox cleanup to allow E2B to process the kill request. This prevents
	 * hitting rate limits when tests run in sequence.
	 */
	private static final long POST_CLEANUP_DELAY_MS = 1000;

	@BeforeEach
	void setUp() {
		logger.info("Creating E2B sandbox for test");
		sandbox = E2BSandbox.builder().timeout(Duration.ofMinutes(2)).build();
		logger.info("Created sandbox: {}", ((E2BSandbox) sandbox).sandboxId());
	}

	@Override
	@AfterEach
	protected void tearDown() throws Exception {
		// Always close sandbox, even if test failed
		if (sandbox != null) {
			String sandboxId = sandbox instanceof E2BSandbox ? ((E2BSandbox) sandbox).sandboxId() : "unknown";
			try {
				if (!sandbox.isClosed()) {
					logger.info("Closing sandbox: {}", sandboxId);
					sandbox.close();
				}
			}
			catch (Exception e) {
				logger.warn("Error closing sandbox {}: {}", sandboxId, e.getMessage());
			}
			finally {
				sandbox = null;
				// Give E2B time to process the kill before next test creates a new
				// sandbox
				Thread.sleep(POST_CLEANUP_DELAY_MS);
			}
		}
	}

}
