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

import java.nio.file.Path;

/**
 * Sandbox interface for executing commands in isolated environments.
 *
 * <p>
 * Provides secure execution of agent commands with proper isolation and resource
 * management. Implementations should ensure commands cannot affect the host system.
 * </p>
 *
 * <p>
 * Supported implementations: {@link LocalSandbox} (local process execution).
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