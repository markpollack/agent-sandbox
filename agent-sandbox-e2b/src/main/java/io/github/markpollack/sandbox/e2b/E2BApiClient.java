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
package io.github.markpollack.sandbox.e2b;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.sandbox.SandboxException;

/**
 * HTTP client for E2B REST API operations.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class E2BApiClient {

	private static final Logger logger = LoggerFactory.getLogger(E2BApiClient.class);

	private static final String API_KEY_HEADER = "X-API-Key";

	private static final String CONTENT_TYPE = "application/json";

	private final E2BConfig config;

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper;

	E2BApiClient(E2BConfig config) {
		this.config = config;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	/**
	 * Creates a new sandbox.
	 * @param templateId the template ID to use
	 * @param timeoutSeconds the sandbox timeout in seconds
	 * @param envVars environment variables for the sandbox
	 * @return the sandbox creation response
	 */
	SandboxResponse createSandbox(String templateId, long timeoutSeconds, Map<String, String> envVars) {
		try {
			CreateSandboxRequest request = new CreateSandboxRequest(templateId, timeoutSeconds, envVars, true);
			String body = objectMapper.writeValueAsString(request);

			HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(config.apiUrl() + "/sandboxes"))
				.header(API_KEY_HEADER, config.apiKey())
				.header("Content-Type", CONTENT_TYPE)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(60))
				.build();

			logger.debug("Creating sandbox with template: {}", templateId);
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 201 && response.statusCode() != 200) {
				throw new SandboxException(
						"Failed to create sandbox: " + response.statusCode() + " - " + response.body());
			}

			SandboxResponse sandboxResponse = objectMapper.readValue(response.body(), SandboxResponse.class);
			logger.debug("Created sandbox: {}", sandboxResponse.sandboxId());
			return sandboxResponse;
		}
		catch (JsonProcessingException e) {
			throw new SandboxException("Failed to serialize sandbox request", e);
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to create sandbox", e);
		}
	}

	/**
	 * Kills a sandbox.
	 * @param sandboxId the sandbox ID to kill
	 */
	void killSandbox(String sandboxId) {
		try {
			HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(config.apiUrl() + "/sandboxes/" + sandboxId))
				.header(API_KEY_HEADER, config.apiKey())
				.DELETE()
				.timeout(Duration.ofSeconds(30))
				.build();

			logger.debug("Killing sandbox: {}", sandboxId);
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			// 204 = success, 404 = already killed (idempotent)
			if (response.statusCode() != 204 && response.statusCode() != 404) {
				throw new SandboxException(
						"Failed to kill sandbox: " + response.statusCode() + " - " + response.body());
			}
			logger.debug("Killed sandbox: {}", sandboxId);
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to kill sandbox: " + sandboxId, e);
		}
	}

	/**
	 * Extends sandbox timeout.
	 * @param sandboxId the sandbox ID
	 * @param timeoutSeconds new timeout in seconds from now
	 */
	void setTimeout(String sandboxId, long timeoutSeconds) {
		try {
			String body = "{\"timeout\":" + timeoutSeconds + "}";

			HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(config.apiUrl() + "/sandboxes/" + sandboxId + "/timeout"))
				.header(API_KEY_HEADER, config.apiKey())
				.header("Content-Type", CONTENT_TYPE)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30))
				.build();

			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 204) {
				throw new SandboxException(
						"Failed to set sandbox timeout: " + response.statusCode() + " - " + response.body());
			}
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to set sandbox timeout: " + sandboxId, e);
		}
	}

	/**
	 * Reconnects to an existing sandbox.
	 * @param sandboxId the sandbox ID to reconnect to
	 * @param timeoutSeconds timeout for the reconnected sandbox
	 * @return the sandbox response
	 */
	SandboxResponse reconnect(String sandboxId, long timeoutSeconds) {
		try {
			String body = "{\"timeout\":" + timeoutSeconds + "}";

			HttpRequest httpRequest = HttpRequest.newBuilder()
				.uri(URI.create(config.apiUrl() + "/sandboxes/" + sandboxId + "/connect"))
				.header(API_KEY_HEADER, config.apiKey())
				.header("Content-Type", CONTENT_TYPE)
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(60))
				.build();

			logger.debug("Reconnecting to sandbox: {}", sandboxId);
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200 && response.statusCode() != 201) {
				throw new SandboxException(
						"Failed to reconnect to sandbox: " + response.statusCode() + " - " + response.body());
			}

			SandboxResponse sandboxResponse = objectMapper.readValue(response.body(), SandboxResponse.class);
			logger.debug("Reconnected to sandbox: {}", sandboxResponse.sandboxId());
			return sandboxResponse;
		}
		catch (JsonProcessingException e) {
			throw new SandboxException("Failed to parse sandbox response", e);
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to reconnect to sandbox: " + sandboxId, e);
		}
	}

	/**
	 * Gets the envd URL for a sandbox.
	 * @param sandboxId the sandbox ID
	 * @param domain the sandbox domain from the creation response
	 * @return the envd URL
	 */
	String getEnvdUrl(String sandboxId, String domain) {
		// Use the domain from the response, or fall back to config
		String actualDomain = domain != null && !domain.isEmpty() ? domain : config.domain();
		// Port 49983 is the standard envd port per E2B SDK
		String url = "https://49983-" + sandboxId + "." + actualDomain;
		logger.debug("Constructed envd URL: {}", url);
		return url;
	}

	/**
	 * Request payload for creating a sandbox.
	 */
	record CreateSandboxRequest(String templateID, long timeout, Map<String, String> envVars, boolean secure) {
	}

	/**
	 * Response from sandbox creation or reconnection.
	 */
	record SandboxResponse(@JsonProperty("sandboxID") String sandboxId, @JsonProperty("templateID") String templateId,
			String domain, String envdAccessToken, String envdVersion) {
	}

}
