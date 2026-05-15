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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.FileSpec;
import io.github.markpollack.sandbox.Sandbox;
import io.github.markpollack.sandbox.SandboxException;
import io.github.markpollack.sandbox.SandboxFiles;

/**
 * E2B cloud sandbox implementation using remote Firecracker microVMs.
 *
 * <p>
 * E2B provides secure, isolated sandbox environments running as microVMs in the cloud.
 * This implementation connects to the E2B API for sandbox lifecycle management and
 * executes commands through the envd service.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * try (Sandbox sandbox = E2BSandbox.builder()
 *         .apiKey(System.getenv("E2B_API_KEY"))
 *         .template("base")
 *         .timeout(Duration.ofMinutes(10))
 *         .build()) {
 *     ExecResult result = sandbox.exec(ExecSpec.of("echo", "Hello from E2B"));
 *     System.out.println(result.stdout());
 * }
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see <a href="https://e2b.dev">E2B Documentation</a>
 */
public final class E2BSandbox implements Sandbox {

	private static final Logger logger = LoggerFactory.getLogger(E2BSandbox.class);

	private static final Path WORK_DIR = Path.of("/home/user");

	private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMinutes(2);

	private final String sandboxId;

	private final E2BConfig config;

	private final E2BApiClient apiClient;

	private final E2BEnvdClient envdClient;

	private final E2BSandboxFiles sandboxFiles;

	private volatile boolean closed = false;

	private E2BSandbox(String sandboxId, E2BConfig config, E2BApiClient apiClient, E2BEnvdClient envdClient) {
		this.sandboxId = sandboxId;
		this.config = config;
		this.apiClient = apiClient;
		this.envdClient = envdClient;
		this.sandboxFiles = new E2BSandboxFiles(this, envdClient, WORK_DIR.toString());
	}

	/**
	 * Creates a builder for E2BSandbox with the API key from environment variable.
	 * @return a new Builder instance
	 * @throws IllegalStateException if E2B_API_KEY is not set
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a builder for E2BSandbox with the specified API key.
	 * @param apiKey the E2B API key
	 * @return a new Builder instance
	 */
	public static Builder builder(String apiKey) {
		return new Builder().apiKey(apiKey);
	}

	/**
	 * Connects to an existing sandbox by ID.
	 * @param sandboxId the sandbox ID to connect to
	 * @return a new Builder configured for reconnection
	 */
	public static Builder connect(String sandboxId) {
		return new Builder().sandboxId(sandboxId);
	}

	@Override
	public Path workDir() {
		return WORK_DIR;
	}

	@Override
	public ExecResult exec(ExecSpec spec) {
		if (closed) {
			throw new IllegalStateException("Sandbox is closed");
		}

		List<String> command = spec.command();
		if (command.isEmpty()) {
			throw new IllegalArgumentException("Command cannot be empty");
		}

		// Handle shell command marker
		List<String> processedCommand = processCommand(command);

		// Merge environment variables
		Map<String, String> envVars = new HashMap<>(spec.env());

		Duration timeout = spec.timeout() != null ? spec.timeout() : DEFAULT_COMMAND_TIMEOUT;

		return envdClient.runCommand(processedCommand, WORK_DIR.toString(), envVars, timeout);
	}

	private List<String> processCommand(List<String> command) {
		if (command.size() >= 2 && "__SHELL_COMMAND__".equals(command.get(0))) {
			// Shell command - wrap in bash -c
			return List.of("bash", "-c", command.get(1));
		}
		return command;
	}

	@Override
	public SandboxFiles files() {
		return sandboxFiles;
	}

	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public boolean shouldCleanupOnClose() {
		return true;
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		logger.debug("Closing E2BSandbox: {}", sandboxId);

		try {
			apiClient.killSandbox(sandboxId);
			logger.debug("Successfully killed sandbox: {}", sandboxId);
		}
		catch (Exception e) {
			logger.warn("Failed to kill sandbox cleanly: {}", sandboxId, e);
		}
	}

	/**
	 * Gets the sandbox ID.
	 * @return the E2B sandbox ID
	 */
	public String sandboxId() {
		return sandboxId;
	}

	@Override
	public String toString() {
		return String.format("E2BSandbox{sandboxId=%s, template=%s, closed=%s}", sandboxId, config.template(), closed);
	}

	/**
	 * Builder for creating E2BSandbox instances.
	 */
	public static class Builder {

		private String apiKey;

		private String template = "base";

		private Duration timeout = Duration.ofMinutes(5);

		private String apiUrl;

		private String domain;

		private Map<String, String> envVars = new HashMap<>();

		private String sandboxId; // For reconnection

		private List<FileSpec> initialFiles = new ArrayList<>();

		/**
		 * Set the E2B API key.
		 * @param apiKey the API key
		 * @return this builder
		 */
		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		/**
		 * Set the sandbox template.
		 * @param template the template ID (default: "base")
		 * @return this builder
		 */
		public Builder template(String template) {
			this.template = template;
			return this;
		}

		/**
		 * Set the sandbox timeout.
		 * @param timeout the timeout duration
		 * @return this builder
		 */
		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		/**
		 * Set the E2B API URL.
		 * @param apiUrl the API URL
		 * @return this builder
		 */
		public Builder apiUrl(String apiUrl) {
			this.apiUrl = apiUrl;
			return this;
		}

		/**
		 * Set the E2B domain.
		 * @param domain the domain
		 * @return this builder
		 */
		public Builder domain(String domain) {
			this.domain = domain;
			return this;
		}

		/**
		 * Add an environment variable for the sandbox.
		 * @param key variable name
		 * @param value variable value
		 * @return this builder
		 */
		public Builder env(String key, String value) {
			this.envVars.put(key, value);
			return this;
		}

		/**
		 * Add multiple environment variables.
		 * @param envVars map of environment variables
		 * @return this builder
		 */
		public Builder env(Map<String, String> envVars) {
			this.envVars.putAll(envVars);
			return this;
		}

		/**
		 * Set sandbox ID for reconnection.
		 * @param sandboxId existing sandbox ID
		 * @return this builder
		 */
		Builder sandboxId(String sandboxId) {
			this.sandboxId = sandboxId;
			return this;
		}

		/**
		 * Add a file to be created when the sandbox is built.
		 * @param path relative path within the sandbox
		 * @param content file content
		 * @return this builder
		 */
		public Builder withFile(String path, String content) {
			this.initialFiles.add(FileSpec.of(path, content));
			return this;
		}

		/**
		 * Add multiple files to be created when the sandbox is built.
		 * @param files list of file specifications
		 * @return this builder
		 */
		public Builder withFiles(List<FileSpec> files) {
			this.initialFiles.addAll(files);
			return this;
		}

		/**
		 * Build the E2BSandbox instance.
		 * @return a new E2BSandbox
		 * @throws SandboxException if the sandbox cannot be created
		 */
		public E2BSandbox build() {
			// Resolve API key from environment if not set
			String resolvedApiKey = apiKey;
			if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
				resolvedApiKey = System.getenv("E2B_API_KEY");
			}
			if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
				throw new IllegalStateException(
						"E2B API key is required. Set via builder or E2B_API_KEY environment variable.");
			}

			E2BConfig.Builder configBuilder = E2BConfig.builder(resolvedApiKey).template(template).timeout(timeout);

			if (apiUrl != null) {
				configBuilder.apiUrl(apiUrl);
			}
			if (domain != null) {
				configBuilder.domain(domain);
			}

			E2BConfig config = configBuilder.build();
			E2BApiClient apiClient = new E2BApiClient(config);

			E2BApiClient.SandboxResponse response;
			if (sandboxId != null) {
				// Reconnect to existing sandbox
				response = apiClient.reconnect(sandboxId, timeout.toSeconds());
			}
			else {
				// Create new sandbox
				response = apiClient.createSandbox(template, timeout.toSeconds(), envVars);
			}

			String envdUrl = apiClient.getEnvdUrl(response.sandboxId(), response.domain());
			E2BEnvdClient envdClient = new E2BEnvdClient(envdUrl, response.envdAccessToken());

			// Wait for the envd service to become ready
			logger.debug("Waiting for envd service to become ready...");
			envdClient.waitForReady();
			logger.debug("Envd service is ready");

			E2BSandbox sandbox = new E2BSandbox(response.sandboxId(), config, apiClient, envdClient);

			// Setup initial files
			if (!initialFiles.isEmpty()) {
				sandbox.files().setup(initialFiles);
			}

			return sandbox;
		}

	}

}
