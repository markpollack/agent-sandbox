/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.sandbox;

/**
 * Specification for a file to be created in a sandbox workspace.
 *
 * @param path relative path within the sandbox working directory
 * @param content file content as a string
 * @author Mark Pollack
 * @since 0.1.0
 */
public record FileSpec(String path, String content) {

	/**
	 * Create a file specification.
	 * @param path relative path within the sandbox
	 * @param content file content
	 * @return a new FileSpec
	 */
	public static FileSpec of(String path, String content) {
		return new FileSpec(path, content);
	}

}
