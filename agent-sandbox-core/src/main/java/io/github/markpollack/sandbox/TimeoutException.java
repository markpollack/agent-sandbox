/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.sandbox;

import java.time.Duration;

/**
 * Exception thrown when a command execution times out.
 */
public class TimeoutException extends RuntimeException {

	private final Duration timeout;

	public TimeoutException(String message, Duration timeout) {
		super(message);
		this.timeout = timeout;
	}

	public TimeoutException(String message, Duration timeout, Throwable cause) {
		super(message, cause);
		this.timeout = timeout;
	}

	public Duration getTimeout() {
		return timeout;
	}

}