/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.sandbox.e2b;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for E2B sandbox connections.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public final class E2BConfig {

	private static final String DEFAULT_API_URL = "https://api.e2b.dev";

	private static final String DEFAULT_DOMAIN = "e2b.dev";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

	private static final String DEFAULT_TEMPLATE = "base";

	private final String apiKey;

	private final String apiUrl;

	private final String domain;

	private final Duration timeout;

	private final String template;

	private E2BConfig(Builder builder) {
		this.apiKey = Objects.requireNonNull(builder.apiKey, "API key cannot be null");
		this.apiUrl = builder.apiUrl != null ? builder.apiUrl : DEFAULT_API_URL;
		this.domain = builder.domain != null ? builder.domain : DEFAULT_DOMAIN;
		this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
		this.template = builder.template != null ? builder.template : DEFAULT_TEMPLATE;
	}

	public String apiKey() {
		return apiKey;
	}

	public String apiUrl() {
		return apiUrl;
	}

	public String domain() {
		return domain;
	}

	public Duration timeout() {
		return timeout;
	}

	public String template() {
		return template;
	}

	/**
	 * Creates a new builder with the API key from the E2B_API_KEY environment variable.
	 * @return a new builder
	 * @throws IllegalStateException if E2B_API_KEY is not set
	 */
	public static Builder builder() {
		String apiKey = System.getenv("E2B_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("E2B_API_KEY environment variable is not set");
		}
		return new Builder().apiKey(apiKey);
	}

	/**
	 * Creates a new builder with the specified API key.
	 * @param apiKey the E2B API key
	 * @return a new builder
	 */
	public static Builder builder(String apiKey) {
		return new Builder().apiKey(apiKey);
	}

	public static class Builder {

		private String apiKey;

		private String apiUrl;

		private String domain;

		private Duration timeout;

		private String template;

		public Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiUrl(String apiUrl) {
			this.apiUrl = apiUrl;
			return this;
		}

		public Builder domain(String domain) {
			this.domain = domain;
			return this;
		}

		public Builder timeout(Duration timeout) {
			this.timeout = timeout;
			return this;
		}

		public Builder template(String template) {
			this.template = template;
			return this;
		}

		public E2BConfig build() {
			return new E2BConfig(this);
		}

	}

}
