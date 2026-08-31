/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.sandbox;

import java.nio.file.Path;

/**
 * Shared synchronous command-execution and workspace-file interface.
 *
 * <p>
 * Isolation is implementation-specific and is not guaranteed by this interface.
 * {@link LocalSandbox} executes directly on the host and provides no security isolation.
 * Callers must select a backend and additional controls appropriate to their threat
 * model.
 * </p>
 *
 * <p>
 * The shared synchronous command and file contract is distinct from optional operations
 * such as {@link #startInteractive(ExecSpec)}, which implementations may not support.
 * </p>
 *
 * <p>
 * File operations are available through the {@link #files()} accessor:
 * </p>
 *
 * <pre>{@code
 * sandbox.files()
 *     .create("src/Main.java", code)
 *     .create("pom.xml", pomContent)
 *     .and()
 *     .exec(ExecSpec.of("mvn", "compile"));
 * }</pre>
 *
 * @see SandboxFiles
 */
public interface Sandbox extends AutoCloseable {

	/**
	 * Execute a command specification in the sandbox and wait for completion.
	 * @param spec the execution specification containing command, environment, etc.
	 * @return the execution result
	 * @throws SandboxException if execution fails (wraps IOException,
	 * InterruptedException, TimeoutException)
	 */
	ExecResult exec(ExecSpec spec);

	/**
	 * Start an interactive process in the sandbox without waiting for completion. This is
	 * used for bidirectional communication where the caller needs access to stdin/stdout
	 * streams for ongoing interaction.
	 *
	 * <p>
	 * The caller is responsible for managing the process lifecycle, including reading
	 * from stdout/stderr and writing to stdin, and eventually destroying the process.
	 * </p>
	 * @param spec the execution specification containing command, environment, etc.
	 * @return the started Process with access to I/O streams
	 * @throws SandboxException if the process fails to start
	 */
	default Process startInteractive(ExecSpec spec) {
		throw new UnsupportedOperationException(
				"Interactive process execution not supported by this sandbox implementation");
	}

	/**
	 * Get the working directory path within the sandbox.
	 * @return the sandbox working directory
	 */
	Path workDir();

	/**
	 * Check if this sandbox has been closed.
	 * @return true if closed, false otherwise
	 */
	boolean isClosed();

	/**
	 * Close the sandbox and release resources.
	 * @throws SandboxException if cleanup fails (wraps IOException)
	 */
	@Override
	void close();

	/**
	 * Get the file operations accessor for this sandbox.
	 * <p>
	 * Provides fluent API for creating, reading, and checking files in the sandbox
	 * working directory.
	 * </p>
	 * @return the SandboxFiles accessor
	 */
	SandboxFiles files();

	/**
	 * Whether this sandbox should delete its working directory on close.
	 * <p>
	 * Returns true for temp directories created by the sandbox, false for user-specified
	 * directories.
	 * </p>
	 * @return true if the working directory will be deleted on close
	 */
	boolean shouldCleanupOnClose();

}
