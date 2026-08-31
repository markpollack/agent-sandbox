/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.sandbox;

/**
 * Runtime exception for all sandbox execution errors. Wraps checked exceptions from
 * underlying system operations.
 */
public class SandboxException extends RuntimeException {

	public SandboxException(String message) {
		super(message);
	}

	public SandboxException(String message, Throwable cause) {
		super(message, cause);
	}

	public SandboxException(Throwable cause) {
		super(cause);
	}

}