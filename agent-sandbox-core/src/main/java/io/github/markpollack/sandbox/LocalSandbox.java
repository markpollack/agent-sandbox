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
package io.github.markpollack.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Sandbox implementation that executes commands directly on the host system.
 *
 * <p>
 * <strong>WARNING:</strong> This implementation provides NO isolation and should only be
 * used when Docker is not available. Commands executed through this sandbox can access
 * and modify the host system.
 * </p>
 *
 * <p>
 * Use the {@link #builder()} for fluent configuration including temp directory creation
 * and initial file setup:
 * </p>
 *
 * <pre>{@code
 * try (Sandbox sandbox = LocalSandbox.builder()
 *         .tempDirectory("test-")
 *         .withFile("src/Main.java", "public class Main {}")
 *         .withFile("pom.xml", pomContent)
 *         .build()) {
 *     ExecResult result = sandbox.exec(ExecSpec.of("mvn", "compile"));
 *     assertTrue(sandbox.files().exists("target/classes/Main.class"));
 * }  // Auto-cleanup on close
 * }</pre>
 */
public final class LocalSandbox implements Sandbox {

	private static final Logger logger = LoggerFactory.getLogger(LocalSandbox.class);

	private final Path workingDirectory;

	private final List<ExecSpecCustomizer> customizers;

	private final boolean cleanupOnClose;

	private final LocalSandboxFiles sandboxFiles;

	private volatile boolean closed = false;

	/**
	 * Creates a LocalSandbox with the current working directory.
	 */
	public LocalSandbox() {
		this(Path.of(System.getProperty("user.dir")), List.of(), false);
	}

	/**
	 * Creates a LocalSandbox with the specified working directory.
	 * @param workingDirectory the working directory for command execution
	 */
	public LocalSandbox(Path workingDirectory) {
		this(workingDirectory, List.of(), false);
	}

	/**
	 * Creates a LocalSandbox with the specified working directory and customizers.
	 * @param workingDirectory the working directory for command execution
	 * @param customizers list of customizers to apply before execution
	 */
	public LocalSandbox(Path workingDirectory, List<ExecSpecCustomizer> customizers) {
		this(workingDirectory, customizers, false);
	}

	/**
	 * Creates a LocalSandbox with full configuration.
	 * @param workingDirectory the working directory for command execution
	 * @param customizers list of customizers to apply before execution
	 * @param cleanupOnClose whether to delete the working directory on close
	 */
	LocalSandbox(Path workingDirectory, List<ExecSpecCustomizer> customizers, boolean cleanupOnClose) {
		this.workingDirectory = workingDirectory;
		this.customizers = List.copyOf(customizers);
		this.cleanupOnClose = cleanupOnClose;
		this.sandboxFiles = new LocalSandboxFiles(this, workingDirectory);
		logger.warn("LocalSandbox created - NO ISOLATION PROVIDED. Commands execute directly on host system.");
	}

	/**
	 * Creates a builder for LocalSandbox with fluent configuration.
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Path workDir() {
		return workingDirectory;
	}

	@Override
	public ExecResult exec(ExecSpec spec) {
		if (closed) {
			throw new IllegalStateException("Sandbox is closed");
		}

		// Ensure working directory exists
		try {
			java.nio.file.Files.createDirectories(workingDirectory);
		}
		catch (IOException e) {
			throw new SandboxException("Failed to create working directory: " + workingDirectory, e);
		}

		var startTime = Instant.now();
		var customizedSpec = applyCustomizers(spec);
		var command = customizedSpec.command();

		if (command.isEmpty()) {
			throw new IllegalArgumentException("Command cannot be null or empty");
		}

		// Handle shell commands
		List<String> finalCommand = processCommand(command);

		// Capture stdout and stderr separately - declared outside try for access in catch
		ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
		ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();

		try {
			// Use zt-exec for robust process execution
			ProcessExecutor executor = new ProcessExecutor().command(finalCommand)
				.directory(workingDirectory.toFile())
				.redirectOutput(stdoutStream)
				.redirectError(stderrStream)
				.destroyOnExit();

			logger.debug("LocalSandbox executing command in directory: {}", workingDirectory);
			logger.debug("LocalSandbox command size: {} args", finalCommand.size());
			for (int i = 0; i < finalCommand.size(); i++) {
				String arg = finalCommand.get(i);
				if (i == finalCommand.size() - 1 && arg.length() > 200) {
					logger.debug("LocalSandbox arg[{}]: {} ... (truncated, length={})", i, arg.substring(0, 200),
							arg.length());
				}
				else {
					logger.debug("LocalSandbox arg[{}]: {}", i, arg);
				}
			}

			// Apply environment variables
			// Merge parent environment with custom variables to preserve PATH and other
			// critical variables
			if (!customizedSpec.env().isEmpty()) {
				logger.debug("LocalSandbox adding environment variables: {}", customizedSpec.env().keySet());
				// Create merged environment: parent + custom (custom variables override
				// parent)
				var mergedEnv = new java.util.HashMap<>(System.getenv());
				mergedEnv.putAll(customizedSpec.env());
				executor.environment(mergedEnv);
			}

			// Handle timeout
			if (customizedSpec.timeout() != null) {
				logger.debug("LocalSandbox timeout: {}", customizedSpec.timeout());
				executor.timeout(customizedSpec.timeout().toMillis(), TimeUnit.MILLISECONDS);
			}

			logger.debug("LocalSandbox about to execute command...");
			ProcessResult result = executor.execute();
			logger.debug("LocalSandbox command completed with exit code: {}", result.getExitValue());
			Duration duration = Duration.between(startTime, Instant.now());

			String stdout = stdoutStream.toString(StandardCharsets.UTF_8);
			String stderr = stderrStream.toString(StandardCharsets.UTF_8);
			return new ExecResult(result.getExitValue(), stdout, stderr, duration);
		}
		catch (org.zeroturnaround.exec.InvalidExitValueException e) {
			// zt-exec throws this for non-zero exit codes when expected values are set
			// The streams are still populated, so we can return the captured output
			Duration duration = Duration.between(startTime, Instant.now());
			String stdout = stdoutStream.toString(StandardCharsets.UTF_8);
			String stderr = stderrStream.toString(StandardCharsets.UTF_8);
			return new ExecResult(e.getExitValue(), stdout, stderr, duration);
		}
		catch (java.util.concurrent.TimeoutException e) {
			io.github.markpollack.sandbox.TimeoutException timeoutException = new io.github.markpollack.sandbox.TimeoutException(
					"Command timed out after " + customizedSpec.timeout(), customizedSpec.timeout());
			throw new SandboxException("Command timed out", timeoutException);
		}
		catch (Exception e) {
			throw new SandboxException("Failed to execute command", e);
		}
	}

	private List<String> processCommand(List<String> command) {
		// Handle special shell command marker
		if (command.size() >= 2 && "__SHELL_COMMAND__".equals(command.get(0))) {
			String shellCmd = command.get(1);
			// Use platform-appropriate shell
			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				return List.of("cmd", "/c", shellCmd);
			}
			else {
				return List.of("bash", "-c", shellCmd);
			}
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
	public Process startInteractive(ExecSpec spec) {
		if (closed) {
			throw new IllegalStateException("Sandbox is closed");
		}

		// Ensure working directory exists
		try {
			java.nio.file.Files.createDirectories(workingDirectory);
		}
		catch (IOException e) {
			throw new SandboxException("Failed to create working directory: " + workingDirectory, e);
		}

		var customizedSpec = applyCustomizers(spec);
		var command = customizedSpec.command();

		if (command.isEmpty()) {
			throw new IllegalArgumentException("Command cannot be null or empty");
		}

		// Handle shell commands
		List<String> finalCommand = processCommand(command);

		try {
			ProcessBuilder pb = new ProcessBuilder(finalCommand);
			pb.directory(workingDirectory.toFile());

			// Merge parent environment with custom variables
			if (!customizedSpec.env().isEmpty()) {
				pb.environment().putAll(customizedSpec.env());
			}

			logger.debug("LocalSandbox starting interactive process: {}", finalCommand.get(0));
			return pb.start();
		}
		catch (IOException e) {
			throw new SandboxException("Failed to start interactive process", e);
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;

		if (cleanupOnClose && workingDirectory != null) {
			try {
				deleteDirectoryRecursively(workingDirectory);
				logger.debug("LocalSandbox cleaned up temp directory: {}", workingDirectory);
			}
			catch (IOException e) {
				logger.warn("Failed to cleanup temp directory: {}", workingDirectory, e);
			}
		}
		logger.debug("LocalSandbox closed");
	}

	private void deleteDirectoryRecursively(Path path) throws IOException {
		if (java.nio.file.Files.exists(path)) {
			java.nio.file.Files.walk(path).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				try {
					java.nio.file.Files.delete(p);
				}
				catch (IOException e) {
					logger.warn("Failed to delete: {}", p, e);
				}
			});
		}
	}

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
		return cleanupOnClose;
	}

	/**
	 * Gets the list of customizers used by this sandbox.
	 * @return immutable list of customizers
	 */
	public List<ExecSpecCustomizer> getCustomizers() {
		return customizers;
	}

	@Override
	public String toString() {
		return String.format("LocalSandbox{workDir=%s, customizers=%d, cleanupOnClose=%s, closed=%s}", workingDirectory,
				customizers.size(), cleanupOnClose, closed);
	}

	/**
	 * Builder for creating LocalSandbox instances with fluent configuration.
	 */
	public static class Builder {

		private Path workingDirectory;

		private List<ExecSpecCustomizer> customizers = new ArrayList<>();

		private List<FileSpec> initialFiles = new ArrayList<>();

		private boolean createTempDirectory = false;

		private String tempPrefix = "sandbox-";

		/**
		 * Set the working directory for the sandbox.
		 * @param path the working directory path
		 * @return this builder
		 */
		public Builder workingDirectory(Path path) {
			this.workingDirectory = path;
			this.createTempDirectory = false;
			return this;
		}

		/**
		 * Create a temporary directory for the sandbox. The directory will be
		 * automatically deleted when the sandbox is closed.
		 * @return this builder
		 */
		public Builder tempDirectory() {
			this.createTempDirectory = true;
			return this;
		}

		/**
		 * Create a temporary directory with a custom prefix. The directory will be
		 * automatically deleted when the sandbox is closed.
		 * @param prefix the prefix for the temp directory name
		 * @return this builder
		 */
		public Builder tempDirectory(String prefix) {
			this.createTempDirectory = true;
			this.tempPrefix = prefix;
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
		 * Build the LocalSandbox instance.
		 * @return a new LocalSandbox
		 * @throws SandboxException if the sandbox cannot be created
		 */
		public LocalSandbox build() {
			Path workDir;
			boolean cleanup;

			if (createTempDirectory) {
				try {
					workDir = java.nio.file.Files.createTempDirectory(tempPrefix);
					cleanup = true;
				}
				catch (IOException e) {
					throw new SandboxException("Failed to create temp directory", e);
				}
			}
			else if (workingDirectory != null) {
				workDir = workingDirectory;
				cleanup = false;
			}
			else {
				workDir = Path.of(System.getProperty("user.dir"));
				cleanup = false;
			}

			LocalSandbox sandbox = new LocalSandbox(workDir, customizers, cleanup);

			// Setup initial files
			if (!initialFiles.isEmpty()) {
				sandbox.files().setup(initialFiles);
			}

			return sandbox;
		}

	}

}