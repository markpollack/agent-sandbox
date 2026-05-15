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

import java.util.List;
import java.util.stream.Stream;

/**
 * Accessor for file operations within a sandbox workspace.
 *
 * <p>
 * Provides a fluent API for creating, reading, and checking files in the sandbox working
 * directory. This accessor pattern keeps the main {@link Sandbox} interface clean while
 * providing comprehensive file management capabilities.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * sandbox.files()
 *     .create("src/Main.java", javaCode)
 *     .create("pom.xml", pomContent)
 *     .createDirectory("target")
 *     .and()  // return to Sandbox
 *     .exec(ExecSpec.of("mvn", "compile"));
 *
 * // Verification
 * assertTrue(sandbox.files().exists("target/classes/Main.class"));
 * }</pre>
 *
 * @author Mark Pollack
 * @since 0.1.0
 * @see Sandbox#files()
 */
public interface SandboxFiles {

	/**
	 * Create a file in the sandbox working directory.
	 * <p>
	 * Parent directories are created automatically if they don't exist.
	 * </p>
	 * @param relativePath path relative to the sandbox working directory
	 * @param content file content
	 * @return this SandboxFiles for method chaining
	 * @throws SandboxException if the file cannot be created
	 */
	SandboxFiles create(String relativePath, String content);

	/**
	 * Create a directory in the sandbox working directory.
	 * <p>
	 * Parent directories are created automatically if they don't exist.
	 * </p>
	 * @param relativePath path relative to the sandbox working directory
	 * @return this SandboxFiles for method chaining
	 * @throws SandboxException if the directory cannot be created
	 */
	SandboxFiles createDirectory(String relativePath);

	/**
	 * Setup multiple files in the sandbox.
	 * <p>
	 * Parent directories are created automatically as needed.
	 * </p>
	 * @param files list of file specifications
	 * @return this SandboxFiles for method chaining
	 * @throws SandboxException if any file cannot be created
	 */
	SandboxFiles setup(List<FileSpec> files);

	/**
	 * Read a file from the sandbox working directory.
	 * @param relativePath path relative to the sandbox working directory
	 * @return file content as a string
	 * @throws SandboxException if the file cannot be read
	 */
	String read(String relativePath);

	/**
	 * Check if a file or directory exists in the sandbox.
	 * @param relativePath path relative to the sandbox working directory
	 * @return true if the path exists, false otherwise
	 */
	boolean exists(String relativePath);

	/**
	 * List files and directories at the specified path.
	 * <p>
	 * Returns immediate children only (depth 1). For recursive listing, use
	 * {@link #list(String, int)}.
	 * </p>
	 * @param relativePath path relative to the sandbox working directory
	 * @return list of file entries at the specified path
	 * @throws SandboxException if the path does not exist or is not a directory
	 */
	List<FileEntry> list(String relativePath);

	/**
	 * List files and directories at the specified path with depth control.
	 * @param relativePath path relative to the sandbox working directory
	 * @param maxDepth maximum depth to traverse (1 for immediate children only)
	 * @return list of file entries up to the specified depth
	 * @throws SandboxException if the path does not exist or is not a directory
	 */
	List<FileEntry> list(String relativePath, int maxDepth);

	/**
	 * Delete a file or empty directory.
	 * <p>
	 * For non-empty directories, use {@link #delete(String, boolean)} with recursive
	 * flag.
	 * </p>
	 * @param relativePath path relative to the sandbox working directory
	 * @return this SandboxFiles for method chaining
	 * @throws SandboxException if the path does not exist or deletion fails
	 */
	SandboxFiles delete(String relativePath);

	/**
	 * Delete a file or directory.
	 * @param relativePath path relative to the sandbox working directory
	 * @param recursive if true, recursively delete directory contents
	 * @return this SandboxFiles for method chaining
	 * @throws SandboxException if the path does not exist or deletion fails
	 */
	SandboxFiles delete(String relativePath, boolean recursive);

	/**
	 * Return to the parent Sandbox for continued method chaining.
	 * @return the parent Sandbox instance
	 */
	Sandbox and();

}
