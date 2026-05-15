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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.FileEntry;
import io.github.markpollack.sandbox.FileType;
import io.github.markpollack.sandbox.SandboxException;
import io.github.markpollack.sandbox.TimeoutException;

/**
 * HTTP client for E2B envd service (command execution and file operations).
 *
 * <p>
 * Uses the Connect protocol over HTTP for communication with the sandbox's envd service.
 * Currently uses JDK HttpClient with CompletableFuture.orTimeout() for timeout handling.
 * </p>
 *
 * <p>
 * <b>Future consideration:</b> OkHttp could provide better timeout support with its
 * callTimeout() feature that applies to the entire request/response cycle including
 * streaming. See: https://square.github.io/okhttp/
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class E2BEnvdClient {

	private static final Logger logger = LoggerFactory.getLogger(E2BEnvdClient.class);

	private static final String CONTENT_TYPE_CONNECT_STREAM = "application/connect+json";

	private static final String CONTENT_TYPE_JSON = "application/json";

	private final String envdUrl;

	private final String accessToken;

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper;

	private static final Duration READY_TIMEOUT = Duration.ofSeconds(60);

	private static final Duration READY_POLL_INTERVAL = Duration.ofMillis(500);

	private static final String HEALTH_ENDPOINT = "/health";

	E2BEnvdClient(String envdUrl, String accessToken) {
		this.envdUrl = envdUrl;
		this.accessToken = accessToken;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	/**
	 * Waits for the envd service to become ready using Awaitility.
	 * @throws SandboxException if the service doesn't become ready within the timeout
	 */
	void waitForReady() {
		try {
			Awaitility.await()
				.atMost(READY_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
				.pollInterval(READY_POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)
				.pollDelay(Duration.ZERO)
				.ignoreExceptions()
				.until(this::isEnvdReady);
			logger.debug("Envd service is ready");
		}
		catch (ConditionTimeoutException e) {
			throw new SandboxException("Envd service did not become ready within " + READY_TIMEOUT.toSeconds()
					+ " seconds. The sandbox may still be starting.", e);
		}
	}

	/**
	 * Checks if the envd service is ready by calling the health endpoint.
	 * @return true if the service is ready
	 */
	private boolean isEnvdReady() {
		try {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + HEALTH_ENDPOINT))
				.GET()
				.timeout(Duration.ofSeconds(5));

			// Only add access token if present (it's optional for unsecured sandboxes)
			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();

			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			// 200 and 204 both indicate success
			if (response.statusCode() == 200 || response.statusCode() == 204) {
				return true;
			}

			if (response.statusCode() == 502) {
				logger.debug("Envd not ready yet (502), waiting...");
			}
			else {
				logger.debug("Unexpected response from envd health: {} - {}", response.statusCode(), response.body());
			}
			return false;
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			logger.debug("Failed to connect to envd: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Executes a command in the sandbox and waits for completion.
	 *
	 * <p>
	 * Uses the Connect protocol with server streaming to receive process events (stdout,
	 * stderr, exit code) from the envd service.
	 * </p>
	 * @param command the command and arguments
	 * @param workDir the working directory
	 * @param envVars environment variables
	 * @param timeout command timeout
	 * @return the execution result
	 */
	ExecResult runCommand(List<String> command, String workDir, Map<String, String> envVars, Duration timeout) {
		Instant startTime = Instant.now();
		try {
			// Build the process config for E2B
			// E2B expects either direct command+args or shell command via bash -l -c
			ProcessConfig processConfig;

			if (command.size() >= 3 && "bash".equals(command.get(0)) && "-c".equals(command.get(1))) {
				// Already a shell command (bash -c "script") - run via bash -l -c
				// directly
				String shellScript = command.get(2);
				processConfig = new ProcessConfig("/bin/bash", List.of("-l", "-c", shellScript),
						envVars != null ? envVars : Map.of(), workDir);
			}
			else {
				// Regular command - join and wrap in bash -l -c
				String cmd = String.join(" ", command);
				processConfig = new ProcessConfig("/bin/bash", List.of("-l", "-c", cmd),
						envVars != null ? envVars : Map.of(), workDir);
			}

			// Create StartRequest per process.proto
			StartRequest request = new StartRequest(processConfig, false);

			String jsonBody = objectMapper.writeValueAsString(request);
			byte[] envelopedBody = encodeEnvelope(jsonBody);

			// Don't set HTTP-level timeout - we'll use CompletableFuture.orTimeout
			// instead
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/process.Process/Start"))
				.header("Content-Type", CONTENT_TYPE_CONNECT_STREAM)
				.header("Connect-Protocol-Version", "1")
				.header("Connect-Content-Encoding", "identity")
				.POST(HttpRequest.BodyPublishers.ofByteArray(envelopedBody));

			// Add access token if present
			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();

			logger.debug("Executing command: {} {} in {}", processConfig.cmd(), processConfig.args(), workDir);

			// Use CompletableFuture with orTimeout for proper timeout handling
			// This ensures the entire operation (including streaming response) is bounded
			HttpResponse<byte[]> response = CompletableFuture.supplyAsync(() -> {
				try {
					return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
				}
				catch (IOException | InterruptedException e) {
					if (e instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					throw new RuntimeException(e);
				}
			}).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS).get();

			Duration duration = Duration.between(startTime, Instant.now());

			if (response.statusCode() != 200) {
				String errorBody = new String(response.body(), StandardCharsets.UTF_8);
				logger.error("Command execution failed: {} - {}", response.statusCode(), errorBody);
				throw new SandboxException("Command execution failed: " + response.statusCode() + " - " + errorBody);
			}

			// Parse streaming response - Connect protocol uses binary envelope format
			return parseStreamingResponse(response.body(), duration);
		}
		catch (ExecutionException e) {
			// Unwrap the cause - orTimeout wraps TimeoutException in ExecutionException
			Throwable cause = e.getCause();
			if (cause instanceof java.util.concurrent.TimeoutException) {
				throw new SandboxException("Command execution timed out",
						new TimeoutException("Command execution timed out after " + timeout, timeout));
			}
			if (cause instanceof RuntimeException && cause.getCause() instanceof IOException) {
				throw new SandboxException("Failed to execute command", cause.getCause());
			}
			throw new SandboxException("Failed to execute command", cause);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SandboxException("Command execution interrupted",
					new TimeoutException("Command execution was interrupted", timeout));
		}
		catch (IOException e) {
			throw new SandboxException("Failed to execute command", e);
		}
	}

	/**
	 * Parses the streaming response from process.Process/Start.
	 * <p>
	 * The response contains ProcessEvent messages with start, data (stdout/stderr), and
	 * end events, each wrapped in a Connect binary envelope.
	 * </p>
	 */
	private ExecResult parseStreamingResponse(byte[] responseBody, Duration duration) throws IOException {
		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();
		int exitCode = -1;

		logger.debug("Response body length: {} bytes", responseBody.length);

		// Parse Connect envelope format and extract JSON messages
		List<String> messages = parseEnvelopes(responseBody);

		logger.debug("Parsed {} messages from envelopes", messages.size());

		for (String json : messages) {
			logger.debug("Parsing event JSON: {}", json);
			EventWrapper wrapper = objectMapper.readValue(json, EventWrapper.class);
			ProcessEvent event = wrapper.event();
			if (event == null) {
				continue;
			}
			if (event.start() != null) {
				logger.debug("Process started with PID: {}", event.start().pid());
			}
			if (event.data() != null) {
				DataEvent data = event.data();
				if (data.stdout() != null) {
					String decoded = decodeBase64(data.stdout());
					logger.debug("Stdout data: [{}]", decoded);
					stdout.append(decoded);
				}
				if (data.stderr() != null) {
					String decoded = decodeBase64(data.stderr());
					logger.debug("Stderr data: [{}]", decoded);
					stderr.append(decoded);
				}
			}
			if (event.end() != null) {
				exitCode = event.end().getExitCode();
				logger.debug("Command completed with exit code: {}", exitCode);
			}
		}

		return new ExecResult(exitCode, stdout.toString(), stderr.toString(), duration);
	}

	/**
	 * Decodes base64-encoded string from the API response.
	 */
	private String decodeBase64(String encoded) {
		if (encoded == null || encoded.isEmpty()) {
			return "";
		}
		try {
			return new String(java.util.Base64.getDecoder().decode(encoded));
		}
		catch (IllegalArgumentException e) {
			// Not base64 encoded, return as-is
			return encoded;
		}
	}

	/**
	 * Encodes a message in Connect streaming envelope format. Format: 1 byte flags + 4
	 * bytes big-endian length + data
	 */
	private byte[] encodeEnvelope(String jsonData) {
		byte[] data = jsonData.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(5 + data.length);
		buffer.put((byte) 0); // flags = 0 for normal data
		buffer.putInt(data.length); // big-endian length
		buffer.put(data);
		return buffer.array();
	}

	/**
	 * Parses Connect streaming envelope format from binary response. Returns list of JSON
	 * strings extracted from envelopes.
	 */
	private List<String> parseEnvelopes(byte[] responseBody) {
		List<String> messages = new ArrayList<>();
		ByteBuffer buffer = ByteBuffer.wrap(responseBody);

		while (buffer.remaining() >= 5) {
			byte flags = buffer.get();
			int length = buffer.getInt();

			if (buffer.remaining() < length) {
				logger.warn("Incomplete envelope: expected {} bytes, have {}", length, buffer.remaining());
				break;
			}

			byte[] data = new byte[length];
			buffer.get(data);
			String json = new String(data, StandardCharsets.UTF_8);

			// flags & 0x02 indicates end-of-stream (trailers)
			if ((flags & 0x02) != 0) {
				logger.debug("Received end-of-stream envelope");
				// Trailers may contain error info, but for now we skip them
				continue;
			}

			messages.add(json);
		}

		return messages;
	}

	/**
	 * Writes a file to the sandbox using the /files REST endpoint.
	 * @param path the file path
	 * @param content the file content
	 */
	void writeFile(String path, String content) {
		try {
			// Use multipart/form-data with the file content
			String boundary = "----E2BFileBoundary" + System.currentTimeMillis();
			byte[] fileContent = content.getBytes(StandardCharsets.UTF_8);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// Multipart header
			baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
			baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + path + "\"\r\n")
				.getBytes(StandardCharsets.UTF_8));
			baos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
			baos.write(fileContent);
			baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

			byte[] multipartBody = baos.toByteArray();

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/files?path=" + java.net.URLEncoder.encode(path, StandardCharsets.UTF_8)))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
				.timeout(Duration.ofSeconds(30));

			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200 && response.statusCode() != 201) {
				throw new SandboxException("Failed to write file: " + response.statusCode() + " - " + response.body());
			}
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to write file: " + path, e);
		}
	}

	/**
	 * Reads a file from the sandbox using the /files REST endpoint.
	 * @param path the file path
	 * @return the file content
	 */
	String readFile(String path) {
		try {
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/files?path=" + java.net.URLEncoder.encode(path, StandardCharsets.UTF_8)))
				.GET()
				.timeout(Duration.ofSeconds(30));

			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				throw new SandboxException("Failed to read file: " + response.statusCode() + " - " + response.body());
			}

			return response.body();
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to read file: " + path, e);
		}
	}

	/**
	 * Lists files in a directory using filesystem.Filesystem/ListDir RPC.
	 * @param path the directory path
	 * @param depth maximum depth (0 = unlimited)
	 * @return list of file entries
	 */
	List<FileEntry> listFiles(String path, int depth) {
		try {
			ListDirRequest request = new ListDirRequest(path, depth);
			String body = objectMapper.writeValueAsString(request);

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/filesystem.Filesystem/ListDir"))
				.header("Content-Type", CONTENT_TYPE_JSON)
				.header("Connect-Protocol-Version", "1")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30));

			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				String errorMsg = response.body();
				// Normalize error message for TCK compatibility
				if (response.statusCode() == 404 || errorMsg.contains("not_found")
						|| errorMsg.contains("no such file")) {
					throw new SandboxException("Directory does not exist: " + path);
				}
				throw new SandboxException("Failed to list files: " + response.statusCode() + " - " + errorMsg);
			}

			logger.debug("ListDir response: {}", response.body());
			ListDirResponse listResponse = objectMapper.readValue(response.body(), ListDirResponse.class);
			List<FileEntry> entries = new ArrayList<>();

			if (listResponse.entries() != null) {
				for (EntryInfo dto : listResponse.entries()) {
					FileType type = mapFileType(dto.type());
					Instant modTime = parseModifiedTime(dto.modifiedTime());
					entries.add(new FileEntry(dto.name(), type, dto.path(), dto.size(), modTime));
				}
			}

			return entries;
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to list files: " + path, e);
		}
	}

	/**
	 * Maps the proto FileType enum string to our FileType enum.
	 */
	private FileType mapFileType(String protoType) {
		if (protoType == null) {
			return FileType.FILE;
		}
		return switch (protoType) {
			case "FILE_TYPE_DIRECTORY" -> FileType.DIRECTORY;
			case "FILE_TYPE_FILE" -> FileType.FILE;
			default -> FileType.FILE;
		};
	}

	/**
	 * Parses the modified_time from proto Timestamp format.
	 */
	private Instant parseModifiedTime(ModifiedTime modTime) {
		if (modTime == null) {
			return Instant.now();
		}
		// Proto Timestamp has seconds and nanos
		return Instant.ofEpochSecond(modTime.seconds(), modTime.nanos());
	}

	/**
	 * Checks if a file exists using filesystem.Filesystem/Stat RPC.
	 * @param path the file path
	 * @return true if exists
	 */
	boolean exists(String path) {
		try {
			StatRequest request = new StatRequest(path);
			String body = objectMapper.writeValueAsString(request);

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/filesystem.Filesystem/Stat"))
				.header("Content-Type", CONTENT_TYPE_JSON)
				.header("Connect-Protocol-Version", "1")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30));

			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			// If Stat succeeds, the file exists
			return response.statusCode() == 200;
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			return false;
		}
	}

	/**
	 * Removes a file or directory. Throws SandboxException if file does not exist.
	 * @param path the path to remove
	 */
	void remove(String path) {
		// Check if file exists first (E2B Remove is idempotent)
		if (!exists(path)) {
			throw new SandboxException("File does not exist: " + path);
		}

		try {
			RemoveRequest request = new RemoveRequest(path);
			String body = objectMapper.writeValueAsString(request);

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/filesystem.Filesystem/Remove"))
				.header("Content-Type", CONTENT_TYPE_JSON)
				.header("Connect-Protocol-Version", "1")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30));

			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				String errorMsg = response.body();
				// Normalize error message for TCK compatibility
				if (response.statusCode() == 404 || errorMsg.contains("not_found")
						|| errorMsg.contains("no such file")) {
					throw new SandboxException("File does not exist: " + path);
				}
				throw new SandboxException("Failed to remove file: " + response.statusCode() + " - " + errorMsg);
			}
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to remove file: " + path, e);
		}
	}

	/**
	 * Creates a directory. Idempotent - ignores "already exists" errors.
	 * @param path the directory path
	 */
	void makeDir(String path) {
		try {
			MakeDirRequest request = new MakeDirRequest(path);
			String body = objectMapper.writeValueAsString(request);

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(envdUrl + "/filesystem.Filesystem/MakeDir"))
				.header("Content-Type", CONTENT_TYPE_JSON)
				.header("Connect-Protocol-Version", "1")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30));

			if (accessToken != null && !accessToken.isEmpty()) {
				requestBuilder.header("X-Access-Token", accessToken);
			}

			HttpRequest httpRequest = requestBuilder.build();
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				// 409 with "already_exists" code is acceptable - directory exists
				if (response.statusCode() == 409 && response.body().contains("already_exists")) {
					logger.debug("Directory already exists (ignored): {}", path);
					return;
				}
				throw new SandboxException(
						"Failed to create directory: " + response.statusCode() + " - " + response.body());
			}
		}
		catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new SandboxException("Failed to create directory: " + path, e);
		}
	}

	// Request/Response DTOs for process.Process/Start (per process.proto)

	record StartRequest(ProcessConfig process, boolean stdin) {
	}

	record ProcessConfig(String cmd, List<String> args, Map<String, String> envs, String cwd) {
	}

	// Streaming response - each message has an "event" wrapper
	record EventWrapper(ProcessEvent event) {
	}

	record ProcessEvent(StartEvent start, DataEvent data, EndEvent end) {
	}

	record StartEvent(int pid) {
	}

	record DataEvent(String stdout, String stderr) {
	}

	record EndEvent(@JsonProperty("exit_code") Integer exitCode, boolean exited, String status) {

		/**
		 * Gets the exit code, parsing from status string if needed.
		 */
		int getExitCode() {
			if (exitCode != null) {
				return exitCode;
			}
			// Parse from status string like "exit status 0"
			if (status != null && status.startsWith("exit status ")) {
				try {
					return Integer.parseInt(status.substring("exit status ".length()).trim());
				}
				catch (NumberFormatException e) {
					return -1;
				}
			}
			return exited ? 0 : -1;
		}
	}

	record WriteFileRequest(String path, String content) {
	}

	record ReadFileRequest(String path) {
	}

	record ReadFileResponse(String content) {
	}

	// Filesystem DTOs matching filesystem.proto

	record ListDirRequest(String path, int depth) {
	}

	record ListDirResponse(List<EntryInfo> entries) {
	}

	record StatRequest(String path) {
	}

	record StatResponse(EntryInfo entry) {
	}

	/**
	 * EntryInfo DTO matching filesystem.proto EntryInfo message. Uses @JsonProperty for
	 * snake_case field names.
	 */
	record EntryInfo(String name, String type, String path, long size, int mode, String permissions, String owner,
			String group, @JsonProperty("modified_time") ModifiedTime modifiedTime,
			@JsonProperty("symlink_target") String symlinkTarget) {
	}

	/**
	 * ModifiedTime DTO matching google.protobuf.Timestamp format.
	 */
	record ModifiedTime(long seconds, int nanos) {
	}

	record RemoveRequest(String path) {
	}

	record MakeDirRequest(String path) {
	}

}
