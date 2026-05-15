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
package io.github.markpollack.sandbox.docker;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.ExecSpecCustomizer;
import io.github.markpollack.sandbox.Sandbox;
import io.github.markpollack.sandbox.SandboxException;
import io.github.markpollack.sandbox.SandboxFiles;
import io.github.markpollack.sandbox.TimeoutException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sandbox implementation that executes commands in Docker containers for strong
 * isolation.
 *
 * <p>
 * Supports {@link ExecSpecCustomizer}s for last-mile command and environment
 * customization. Each execution applies all customizers in sequence before running the
 * command in the container.
 *
 * <p>
 * The container runs with a long-lived "sleep infinity" process, allowing multiple
 * command executions within the same container environment.
 *
 * <p>
 * Use the {@link #builder()} for fluent configuration including initial file setup:
 * </p>
 *
 * <pre>{@code
 * try (Sandbox sandbox = DockerSandbox.builder()
 *         .image("ghcr.io/spring-ai-community/agents-runtime:latest")
 *         .withFile("src/Main.java", "public class Main {}")
 *         .withFile("pom.xml", pomContent)
 *         .build()) {
 *     ExecResult result = sandbox.exec(ExecSpec.of("mvn", "compile"));
 *     assertTrue(sandbox.files().exists("target/classes/Main.class"));
 * }  // Auto-cleanup on close
 * }</pre>
 */
public final class DockerSandbox implements Sandbox {

	private static final Logger logger = LoggerFactory.getLogger(DockerSandbox.class);

	private static final String DEFAULT_IMAGE = "ghcr.io/spring-ai-community/agents-runtime:latest";

	private static final Path WORK_DIR = Path.of("/work");

	private final GenericContainer<?> container;

	private final List<ExecSpecCustomizer> customizers;

	private final DockerSandboxFiles sandboxFiles;

	private volatile boolean closed = false;

	/**
	 * Creates a DockerSandbox with the default agents runtime image and no customizers.
	 */
	public DockerSandbox() {
		this(DEFAULT_IMAGE, List.of());
	}

	/**
	 * Creates a DockerSandbox with no customizers.
	 * @param baseImage the Docker image to use for the container
	 */
	public DockerSandbox(String baseImage) {
		this(baseImage, List.of());
	}

	/**
	 * Creates a DockerSandbox with the specified customizers.
	 * @param baseImage the Docker image to use for the container
	 * @param customizers list of customizers to apply before execution
	 */
	public DockerSandbox(String baseImage, List<ExecSpecCustomizer> customizers) {
		this.customizers = List.copyOf(customizers);
		this.container = new GenericContainer<>(DockerImageName.parse(baseImage)).withWorkingDirectory("/work")
			.withCommand("sleep", "infinity");

		container.start();
		this.sandboxFiles = new DockerSandboxFiles(this, container);
		logger.debug("Started DockerSandbox with image: {} and {} customizers", baseImage, customizers.size());
	}

	/**
	 * Creates a builder for DockerSandbox with fluent configuration.
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
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

		var startTime = Instant.now();
		var customizedSpec = applyCustomizers(spec);
		var command = customizedSpec.command();
		if (command.isEmpty()) {
			throw new IllegalArgumentException("Command cannot be null or empty");
		}

		// Handle shell commands
		List<String> processedCommand = processCommand(command);

		// The most robust way to pass arguments to a shell is via positional parameters.
		// We use 'exec "$@"' to replace the shell process with the command,
		// which also ensures the exit code is passed through correctly.
		List<String> finalCommandList = new ArrayList<>();
		finalCommandList.add("bash");
		finalCommandList.add("-lc");
		finalCommandList.add("exec \"$@\""); // The script to execute
		finalCommandList.add("bash"); // This becomes $0 for the script
		finalCommandList.addAll(processedCommand); // These become $1, $2, ...

		try {
			// For environment variables, we need to set them in the shell command
			// since TestContainers execInContainer doesn't support env variables directly
			List<String> commandWithEnv = new ArrayList<>();
			commandWithEnv.add("bash");
			commandWithEnv.add("-lc");

			// Build shell command that sets environment variables and then executes the
			// command
			StringBuilder shellScript = new StringBuilder();
			for (var entry : customizedSpec.env().entrySet()) {
				shellScript.append("export ")
					.append(entry.getKey())
					.append("='")
					.append(entry.getValue())
					.append("'; ");
			}
			shellScript.append("exec \"$@\"");

			commandWithEnv.add(shellScript.toString());
			commandWithEnv.add("bash"); // This becomes $0 for the script
			commandWithEnv.addAll(processedCommand); // These become $1, $2, ...

			// Execute with timeout
			org.testcontainers.containers.Container.ExecResult result;
			if (customizedSpec.timeout() != null) {
				// TestContainers doesn't have built-in timeout support for
				// execInContainer,
				// so we need to implement it ourselves
				var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
					try {
						return this.container.execInContainer(commandWithEnv.toArray(new String[0]));
					}
					catch (Exception e) {
						throw new RuntimeException(e);
					}
				});

				try {
					result = future.get(customizedSpec.timeout().toMillis(),
							java.util.concurrent.TimeUnit.MILLISECONDS);
				}
				catch (java.util.concurrent.TimeoutException e) {
					future.cancel(true);
					throw new TimeoutException("Command timed out after " + customizedSpec.timeout(),
							customizedSpec.timeout());
				}
				catch (java.util.concurrent.ExecutionException e) {
					Throwable cause = e.getCause();
					if (cause instanceof RuntimeException) {
						throw (RuntimeException) cause;
					}
					throw new IOException("Failed to execute command", cause);
				}
			}
			else {
				// No timeout specified
				result = this.container.execInContainer(commandWithEnv.toArray(new String[0]));
			}

			var duration = Duration.between(startTime, Instant.now());
			return new ExecResult(result.getExitCode(), result.getStdout(), result.getStderr(), duration);
		}
		catch (TimeoutException e) {
			throw new SandboxException("Command timed out", e);
		}
		catch (Exception e) {
			throw new SandboxException("Failed to execute command in container", e);
		}
	}

	private List<String> processCommand(List<String> command) {
		// Handle special shell command marker
		if (command.size() >= 2 && "__SHELL_COMMAND__".equals(command.get(0))) {
			String shellCmd = command.get(1);
			// In Docker, always use bash
			return List.of("bash", "-c", shellCmd);
		}
		return command;
	}

	private ExecSpec applyCustomizers(ExecSpec spec) {
		ExecSpec customizedSpec = spec;
		for (ExecSpecCustomizer customizer : customizers) {
			customizedSpec = customizer.customize(customizedSpec);
		}
		return customizedSpec;
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		logger.debug("Stopping DockerSandbox container");

		try {
			container.stop();
			logger.debug("Successfully stopped container");
		}
		catch (Exception e) {
			logger.warn("Failed to stop container cleanly", e);
			throw new SandboxException("Failed to close DockerSandbox", e);
		}
	}

	/**
	 * Gets the list of customizers used by this sandbox.
	 * @return immutable list of customizers
	 */
	public List<ExecSpecCustomizer> getCustomizers() {
		return customizers;
	}

	/** Checks if this sandbox has been closed. */
	@Override
	public boolean isClosed() {
		return closed;
	}

	@Override
	public SandboxFiles files() {
		return sandboxFiles;
	}

	@Override
	public boolean shouldCleanupOnClose() {
		// Docker containers always clean up their workspace
		return true;
	}

	/**
	 * Gets the Docker container used by this sandbox. Useful for advanced container
	 * operations.
	 * @return the underlying container
	 */
	public GenericContainer<?> getContainer() {
		return container;
	}

	@Override
	public String toString() {
		return String.format("DockerSandbox{image=%s, customizers=%d, closed=%s}", container.getDockerImageName(),
				customizers.size(), closed);
	}

	/**
	 * Builder for creating DockerSandbox instances with fluent configuration.
	 */
	public static class Builder {

		private String image = DEFAULT_IMAGE;

		private List<ExecSpecCustomizer> customizers = new ArrayList<>();

		private List<io.github.markpollack.sandbox.FileSpec> initialFiles = new ArrayList<>();

		/**
		 * Set the Docker image to use for the sandbox.
		 * @param image the Docker image name
		 * @return this builder
		 */
		public Builder image(String image) {
			this.image = image;
			return this;
		}

		/**
		 * Add an ExecSpec customizer.
		 * @param customizer the customizer to add
		 * @return this builder
		 */
		public Builder customizer(ExecSpecCustomizer customizer) {
			this.customizers.add(customizer);
			return this;
		}

		/**
		 * Add a file to be created when the sandbox is built.
		 * @param path relative path within the sandbox
		 * @param content file content
		 * @return this builder
		 */
		public Builder withFile(String path, String content) {
			this.initialFiles.add(io.github.markpollack.sandbox.FileSpec.of(path, content));
			return this;
		}

		/**
		 * Add multiple files to be created when the sandbox is built.
		 * @param files list of file specifications
		 * @return this builder
		 */
		public Builder withFiles(List<io.github.markpollack.sandbox.FileSpec> files) {
			this.initialFiles.addAll(files);
			return this;
		}

		/**
		 * Build the DockerSandbox instance.
		 * @return a new DockerSandbox
		 * @throws SandboxException if the sandbox cannot be created
		 */
		public DockerSandbox build() {
			DockerSandbox sandbox = new DockerSandbox(image, customizers);

			// Setup initial files
			if (!initialFiles.isEmpty()) {
				sandbox.files().setup(initialFiles);
			}

			return sandbox;
		}

	}

}
