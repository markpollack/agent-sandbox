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

import java.time.Duration;
import java.util.Objects;

/**
 * Result of executing a command via a {@link Sandbox}.
 *
 * <p>
 * Provides separate access to stdout and stderr streams for programmatic analysis, while
 * also offering a merged view for AI agent evaluation where the combined output is
 * analyzed by LLMs in a single pass.
 * </p>
 *
 * @param exitCode the process exit code (0 typically indicates success)
 * @param stdout the standard output stream content
 * @param stderr the standard error stream content
 * @param duration the wall-clock time taken to execute the command
 */
public record ExecResult(int exitCode, String stdout, String stderr, Duration duration) {

	public ExecResult {
		Objects.requireNonNull(stdout, "stdout cannot be null");
		Objects.requireNonNull(stderr, "stderr cannot be null");
		Objects.requireNonNull(duration, "duration cannot be null");
	}

	/**
	 * Gets the merged output of stdout and stderr.
	 * <p>
	 * Useful for AI agent evaluation where the combined output is analyzed in a single
	 * pass. Note that this is a simple concatenation (stdout + stderr), not temporally
	 * interleaved.
	 * </p>
	 * @return combined stdout and stderr content
	 */
	public String mergedLog() {
		return stdout + stderr;
	}

	/**
	 * Indicates whether the command executed successfully.
	 * @return true if exit code is 0, false otherwise
	 */
	public boolean success() {
		return exitCode == 0;
	}

	/**
	 * Indicates whether the command failed.
	 * @return true if exit code is non-zero, false otherwise
	 */
	public boolean failed() {
		return !success();
	}

	/**
	 * Checks if the execution produced any output (stdout or stderr).
	 * @return true if either stdout or stderr is not empty, false otherwise
	 */
	public boolean hasOutput() {
		return !stdout.isEmpty() || !stderr.isEmpty();
	}

	/**
	 * Checks if the execution produced any stdout output.
	 * @return true if stdout is not empty, false otherwise
	 */
	public boolean hasStdout() {
		return !stdout.isEmpty();
	}

	/**
	 * Checks if the execution produced any stderr output.
	 * @return true if stderr is not empty, false otherwise
	 */
	public boolean hasStderr() {
		return !stderr.isEmpty();
	}

	/**
	 * Gets the total length of output (stdout + stderr). Useful for metrics and
	 * determining if output was truncated.
	 * @return combined length of stdout and stderr in characters
	 */
	public int outputLength() {
		return stdout.length() + stderr.length();
	}

	/**
	 * Creates a summary string suitable for logging or display. Does not include the full
	 * output to avoid log spam.
	 * @return concise summary of the execution result
	 */
	public String summary() {
		return String.format("ExecResult{exitCode=%d, success=%s, duration=%s, stdoutLen=%d, stderrLen=%d}", exitCode,
				success(), duration, stdout.length(), stderr.length());
	}

	@Override
	public String toString() {
		// For debugging - includes full output but marks it clearly
		return String.format("ExecResult{exitCode=%d, duration=%s, stdout='%s', stderr='%s'}", exitCode, duration,
				stdout, stderr);
	}

}