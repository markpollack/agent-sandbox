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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test Compatibility Kit (TCK) for testing any Sandbox implementation.
 *
 * <p>
 * This abstract test class defines the standard test suite that all Sandbox
 * implementations must pass. Concrete test classes should extend this class and provide
 * the specific Sandbox implementation in their setup methods.
 * </p>
 *
 * <p>
 * The TCK ensures consistent behavior across all sandbox implementations including: -
 * Basic command execution - Environment variable handling - Working directory behavior -
 * Timeout handling - Error handling - Resource isolation - Resource cleanup
 * </p>
 */
public abstract class AbstractSandboxTCK {

	/**
	 * The sandbox implementation under test. Must be set by concrete test classes.
	 */
	protected Sandbox sandbox;

	/**
	 * Cleanup after each test to ensure resource isolation. Subclasses may override this
	 * to customize cleanup behavior (e.g., for shared sandbox implementations).
	 */
	@AfterEach
	protected void tearDown() throws Exception {
		if (sandbox != null && !sandbox.isClosed()) {
			sandbox.close();
		}
	}

	/**
	 * Test basic command execution functionality. Verifies that the sandbox can execute
	 * simple commands and return results.
	 */
	@Test
	void testBasicExecution() throws Exception {
		// Arrange
		ExecSpec echoTest = ExecSpec.builder()
			.command("echo", "Hello from sandbox")
			.timeout(Duration.ofSeconds(30))
			.build();

		// Act
		ExecResult result = sandbox.exec(echoTest);

		// Assert
		assertThat(result.success()).isTrue();
		assertThat(result.mergedLog()).contains("Hello from sandbox");
		assertThat(result.exitCode()).isEqualTo(0);
		assertThat(result.duration()).isPositive();
		assertThat(result.hasOutput()).isTrue();
	}

	/**
	 * Test environment variable injection functionality. Verifies that environment
	 * variables are properly passed to executed commands.
	 */
	@Test
	void testEnvironmentVariables() throws Exception {
		// Arrange
		ExecSpec envTest = ExecSpec.builder()
			.command("printenv", "TEST_VAR")
			.env(Map.of("TEST_VAR", "test-value"))
			.timeout(Duration.ofSeconds(30))
			.build();

		// Act
		ExecResult result = sandbox.exec(envTest);

		// Assert
		assertThat(result.success()).isTrue();
		assertThat(result.mergedLog().trim()).isEqualTo("test-value");
	}

	/**
	 * Test working directory functionality. Verifies that the sandbox working directory
	 * is properly configured.
	 */
	@Test
	void testWorkingDirectory() throws Exception {
		// Arrange
		ExecSpec pwdTest = ExecSpec.builder().command("pwd").timeout(Duration.ofSeconds(30)).build();

		// Act
		ExecResult result = sandbox.exec(pwdTest);

		// Assert
		assertThat(result.success()).isTrue();
		assertThat(result.mergedLog().trim()).isNotEmpty();
		// Verify the working directory matches what the sandbox reports
		assertThat(result.mergedLog().trim()).isEqualTo(sandbox.workDir().toString());
	}

	/**
	 * Test timeout handling functionality. Verifies that commands that exceed their
	 * timeout are properly terminated.
	 */
	@Test
	void testTimeoutHandling() {
		// Arrange: Command that takes longer than timeout
		ExecSpec timeoutTest = ExecSpec.builder().command("sleep", "10").timeout(Duration.ofSeconds(2)).build();

		// Act & Assert: Should throw SandboxException wrapping TimeoutException
		try {
			sandbox.exec(timeoutTest);
			fail("Expected SandboxException wrapping TimeoutException");
		}
		catch (SandboxException e) {
			// Should wrap TimeoutException
			assertThat(e.getCause()).isInstanceOf(TimeoutException.class);
			TimeoutException timeoutException = (TimeoutException) e.getCause();
			assertThat(timeoutException.getTimeout()).isEqualTo(Duration.ofSeconds(2));
		}
	}

	/**
	 * Test error handling functionality. Verifies that commands with non-zero exit codes
	 * are properly handled and stderr is captured.
	 */
	@Test
	void testErrorHandling() throws Exception {
		// Arrange: Command that will fail
		ExecSpec failTest = ExecSpec.builder()
			.command("ls", "/nonexistent-directory")
			.timeout(Duration.ofSeconds(30))
			.build();

		// Act
		ExecResult result = sandbox.exec(failTest);

		// Assert: Non-zero exit code handled correctly
		assertThat(result.failed()).isTrue();
		assertThat(result.exitCode()).isNotEqualTo(0);
		// Error message should be in stderr
		assertThat(result.stderr()).contains("No such file or directory");
		// mergedLog should also contain it for backwards compatibility
		assertThat(result.mergedLog()).contains("No such file or directory");
	}

	/**
	 * Test stdout/stderr separation. Verifies that stdout content is captured in the
	 * stdout field.
	 */
	@Test
	void testStdoutCapture() throws Exception {
		// Arrange
		ExecSpec echoTest = ExecSpec.builder()
			.command("echo", "stdout-content")
			.timeout(Duration.ofSeconds(30))
			.build();

		// Act
		ExecResult result = sandbox.exec(echoTest);

		// Assert
		assertThat(result.success()).isTrue();
		assertThat(result.stdout()).contains("stdout-content");
		assertThat(result.hasStdout()).isTrue();
		// echo should not produce stderr
		assertThat(result.stderr()).isEmpty();
		assertThat(result.hasStderr()).isFalse();
		// mergedLog should equal stdout when stderr is empty
		assertThat(result.mergedLog()).isEqualTo(result.stdout());
	}

	/**
	 * Test stderr capture. Verifies that error output is captured in the stderr field.
	 */
	@Test
	void testStderrCapture() throws Exception {
		// Arrange: Command that writes to stderr
		ExecSpec stderrTest = ExecSpec.builder()
			.shellCommand("echo 'error-message' >&2")
			.timeout(Duration.ofSeconds(30))
			.build();

		// Act
		ExecResult result = sandbox.exec(stderrTest);

		// Assert
		assertThat(result.success()).isTrue();
		assertThat(result.stderr()).contains("error-message");
		assertThat(result.hasStderr()).isTrue();
		// Should not appear in stdout
		assertThat(result.stdout()).doesNotContain("error-message");
	}

	/**
	 * Test mixed stdout/stderr output. Verifies that both streams are captured
	 * separately.
	 */
	@Test
	void testMixedStdoutStderr() throws Exception {
		// Arrange: Command that writes to both stdout and stderr
		ExecSpec mixedTest = ExecSpec.builder()
			.shellCommand("echo 'to-stdout' && echo 'to-stderr' >&2")
			.timeout(Duration.ofSeconds(30))
			.build();

		// Act
		ExecResult result = sandbox.exec(mixedTest);

		// Assert
		assertThat(result.success()).isTrue();
		assertThat(result.stdout()).contains("to-stdout");
		assertThat(result.stderr()).contains("to-stderr");
		// mergedLog should contain both
		assertThat(result.mergedLog()).contains("to-stdout");
		assertThat(result.mergedLog()).contains("to-stderr");
		// Verify they're in the correct streams (not swapped)
		assertThat(result.stdout()).doesNotContain("to-stderr");
		assertThat(result.stderr()).doesNotContain("to-stdout");
	}

	/**
	 * Test multiple command execution functionality. Verifies that multiple commands can
	 * be executed sequentially in the same sandbox.
	 */
	@Test
	void testMultipleExecutions() throws Exception {
		// Act: Execute multiple commands in same sandbox
		ExecResult result1 = sandbox.exec(ExecSpec.builder().command("echo", "first").build());
		ExecResult result2 = sandbox.exec(ExecSpec.builder().command("echo", "second").build());
		ExecResult result3 = sandbox.exec(ExecSpec.builder().command("echo", "third").build());

		// Assert: All executions succeed
		assertThat(result1.success()).isTrue();
		assertThat(result2.success()).isTrue();
		assertThat(result3.success()).isTrue();
		assertThat(result1.mergedLog()).contains("first");
		assertThat(result2.mergedLog()).contains("second");
		assertThat(result3.mergedLog()).contains("third");
	}

	/**
	 * Test resource isolation functionality. Verifies that the sandbox provides proper
	 * isolation from the host system.
	 */
	@Test
	void testResourceIsolation() throws Exception {
		// Arrange: Try to access host filesystem (behavior varies by sandbox type)
		ExecSpec isolationTest = ExecSpec.builder().command("ls", "/home").timeout(Duration.ofSeconds(30)).build();

		// Act
		ExecResult result = sandbox.exec(isolationTest);

		// Assert: Command executes (isolation level depends on sandbox implementation)
		assertThat(result.success()).isTrue();
		assertThat(result.mergedLog()).isNotNull();
		// Note: The exact isolation behavior will vary between LocalSandbox and
		// DockerSandbox
	}

	/**
	 * Test sandbox state management functionality. Verifies that the sandbox properly
	 * tracks its open/closed state.
	 */
	@Test
	void testSandboxStateManagement() {
		// Assert: Sandbox starts in open state
		assertThat(sandbox.isClosed()).isFalse();
		assertThat(sandbox.workDir()).isNotNull();
	}

	/**
	 * Test resource cleanup functionality. Verifies that the sandbox properly releases
	 * resources when closed.
	 */
	@Test
	void testResourceCleanup() throws Exception {
		// Arrange: Verify sandbox is initially open
		assertThat(sandbox.isClosed()).isFalse();

		// Act: Close the sandbox
		sandbox.close();

		// Assert: Sandbox is marked as closed
		assertThat(sandbox.isClosed()).isTrue();
	}

	/**
	 * Test directory listing functionality. Verifies that files and directories can be
	 * listed.
	 */
	@Test
	void testListDirectory() throws Exception {
		// Arrange: Create some files and directories
		sandbox.files().create("file1.txt", "content1").create("file2.txt", "content2").createDirectory("subdir");

		// Act: List the root directory
		List<FileEntry> entries = sandbox.files().list(".");

		// Assert: Should contain the created files and directory
		// Use contains() instead of containsExactlyInAnyOrder() to allow for
		// sandbox-specific files (e.g., E2B has dotfiles in /home/user)
		assertThat(entries).hasSizeGreaterThanOrEqualTo(3);
		assertThat(entries).extracting(FileEntry::name).contains("file1.txt", "file2.txt", "subdir");

		// Verify file types
		assertThat(entries.stream().filter(e -> e.name().equals("file1.txt")).findFirst().get().isFile()).isTrue();
		assertThat(entries.stream().filter(e -> e.name().equals("subdir")).findFirst().get().isDirectory()).isTrue();
	}

	/**
	 * Test directory listing with depth. Verifies that recursive listing works correctly.
	 */
	@Test
	void testListDirectoryWithDepth() throws Exception {
		// Arrange: Create nested structure
		sandbox.files()
			.create("root.txt", "root")
			.createDirectory("level1")
			.create("level1/file1.txt", "level1 content")
			.createDirectory("level1/level2")
			.create("level1/level2/file2.txt", "level2 content");

		// Act: List with depth 1 (immediate children only)
		List<FileEntry> depth1 = sandbox.files().list(".", 1);

		// Assert: Should see root level items (use contains to allow sandbox-specific
		// files)
		assertThat(depth1).extracting(FileEntry::name).contains("root.txt", "level1");

		// Act: List with depth 2
		List<FileEntry> depth2 = sandbox.files().list(".", 2);

		// Assert: Should see root and level1 contents
		assertThat(depth2).extracting(FileEntry::name).contains("root.txt", "level1", "file1.txt", "level2");
	}

	/**
	 * Test listing non-existent directory. Verifies that appropriate exception is thrown.
	 */
	@Test
	void testListNonExistentDirectory() {
		// Act & Assert: Should throw SandboxException
		assertThatThrownBy(() -> sandbox.files().list("nonexistent")).isInstanceOf(SandboxException.class)
			.hasMessageContaining("does not exist");
	}

	/**
	 * Test listing a file (not directory). Verifies that appropriate exception is thrown.
	 */
	@Test
	void testListFileNotDirectory() throws Exception {
		// Arrange: Create a file
		sandbox.files().create("afile.txt", "content");

		// Act & Assert: Should throw SandboxException
		assertThatThrownBy(() -> sandbox.files().list("afile.txt")).isInstanceOf(SandboxException.class)
			.hasMessageContaining("not a directory");
	}

	/**
	 * Test file deletion. Verifies that files can be deleted.
	 */
	@Test
	void testDeleteFile() throws Exception {
		// Arrange: Create a file
		sandbox.files().create("todelete.txt", "content");
		assertThat(sandbox.files().exists("todelete.txt")).isTrue();

		// Act: Delete the file
		sandbox.files().delete("todelete.txt");

		// Assert: File should no longer exist
		assertThat(sandbox.files().exists("todelete.txt")).isFalse();
	}

	/**
	 * Test empty directory deletion. Verifies that empty directories can be deleted
	 * without recursive flag.
	 */
	@Test
	void testDeleteEmptyDirectory() throws Exception {
		// Arrange: Create an empty directory
		sandbox.files().createDirectory("emptydir");
		assertThat(sandbox.files().exists("emptydir")).isTrue();

		// Act: Delete the directory
		sandbox.files().delete("emptydir");

		// Assert: Directory should no longer exist
		assertThat(sandbox.files().exists("emptydir")).isFalse();
	}

	/**
	 * Test recursive directory deletion. Verifies that directories with contents can be
	 * deleted with recursive flag.
	 */
	@Test
	void testDeleteDirectoryRecursive() throws Exception {
		// Arrange: Create directory with contents
		sandbox.files()
			.createDirectory("dirwithcontent")
			.create("dirwithcontent/file1.txt", "content1")
			.create("dirwithcontent/file2.txt", "content2")
			.createDirectory("dirwithcontent/subdir")
			.create("dirwithcontent/subdir/nested.txt", "nested");

		assertThat(sandbox.files().exists("dirwithcontent")).isTrue();
		assertThat(sandbox.files().exists("dirwithcontent/subdir/nested.txt")).isTrue();

		// Act: Delete recursively
		sandbox.files().delete("dirwithcontent", true);

		// Assert: Directory and all contents should be gone
		assertThat(sandbox.files().exists("dirwithcontent")).isFalse();
	}

	/**
	 * Test deleting non-existent path. Verifies that appropriate exception is thrown.
	 */
	@Test
	void testDeleteNonExistent() {
		// Act & Assert: Should throw SandboxException
		assertThatThrownBy(() -> sandbox.files().delete("nonexistent")).isInstanceOf(SandboxException.class)
			.hasMessageContaining("does not exist");
	}

	/**
	 * Test delete method chaining. Verifies that delete returns the SandboxFiles for
	 * chaining.
	 */
	@Test
	void testDeleteChaining() throws Exception {
		// Arrange: Create multiple files
		sandbox.files().create("chain1.txt", "content1").create("chain2.txt", "content2");

		// Act: Delete with chaining
		sandbox.files().delete("chain1.txt").delete("chain2.txt");

		// Assert: Both files should be deleted
		assertThat(sandbox.files().exists("chain1.txt")).isFalse();
		assertThat(sandbox.files().exists("chain2.txt")).isFalse();
	}

}