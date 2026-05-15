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

package io.github.markpollack.sandbox.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import io.github.markpollack.sandbox.ExecResult;
import io.github.markpollack.sandbox.ExecSpec;
import io.github.markpollack.sandbox.FileSpec;
import io.github.markpollack.sandbox.Sandbox;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DockerSandboxFiles}.
 *
 * <p>
 * Run with: mvn test -Dtest=DockerSandboxFilesTest -Dsandbox.infrastructure.test=true
 * </p>
 */
@EnabledIfSystemProperty(named = "sandbox.infrastructure.test", matches = "true")
class DockerSandboxFilesTest {

	@Test
	void createFileShouldCreateFileWithContent() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			sandbox.files().create("test.txt", "Hello, World!");

			assertThat(sandbox.files().exists("test.txt")).isTrue();
			assertThat(sandbox.files().read("test.txt")).isEqualTo("Hello, World!");
		}
	}

	@Test
	void createFileShouldCreateParentDirectories() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			sandbox.files().create("src/main/java/Test.java", "public class Test {}");

			assertThat(sandbox.files().exists("src/main/java/Test.java")).isTrue();
			assertThat(sandbox.files().exists("src/main/java")).isTrue();
			assertThat(sandbox.files().exists("src/main")).isTrue();
			assertThat(sandbox.files().exists("src")).isTrue();
		}
	}

	@Test
	void createDirectoryShouldCreateNestedDirectories() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			sandbox.files().createDirectory("target/classes/com/example");

			assertThat(sandbox.files().exists("target/classes/com/example")).isTrue();
		}
	}

	@Test
	void setupShouldCreateMultipleFiles() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			List<FileSpec> files = List.of(FileSpec.of("pom.xml", "<project/>"),
					FileSpec.of("src/Main.java", "public class Main {}"), FileSpec.of("README.md", "# README"));

			sandbox.files().setup(files);

			assertThat(sandbox.files().exists("pom.xml")).isTrue();
			assertThat(sandbox.files().exists("src/Main.java")).isTrue();
			assertThat(sandbox.files().exists("README.md")).isTrue();
			assertThat(sandbox.files().read("pom.xml")).isEqualTo("<project/>");
		}
	}

	@Test
	void existsShouldReturnFalseForNonexistentFile() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			assertThat(sandbox.files().exists("nonexistent.txt")).isFalse();
		}
	}

	@Test
	void andShouldReturnParentSandbox() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			Sandbox returned = sandbox.files().create("test.txt", "content").and();

			assertThat(returned).isSameAs(sandbox);
		}
	}

	@Test
	void fluentChainingWithExec() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			ExecResult result = sandbox.files()
				.create("script.sh", "#!/bin/bash\necho 'Hello from script'")
				.and()
				.exec(ExecSpec.builder().shellCommand("cat script.sh").build());

			assertThat(result.success()).isTrue();
			assertThat(result.mergedLog()).contains("Hello from script");
		}
	}

	@Test
	void builderWithFileShouldCreateFilesOnBuild() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.withFile("config.txt", "key=value")
			.withFile("data.json", "{}")
			.build()) {

			assertThat(sandbox.files().exists("config.txt")).isTrue();
			assertThat(sandbox.files().exists("data.json")).isTrue();
			assertThat(sandbox.files().read("config.txt")).isEqualTo("key=value");
		}
	}

	@Test
	void builderWithFilesShouldCreateMultipleFiles() {
		List<FileSpec> files = List.of(FileSpec.of("a.txt", "A"), FileSpec.of("b.txt", "B"));

		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.withFiles(files)
			.build()) {

			assertThat(sandbox.files().exists("a.txt")).isTrue();
			assertThat(sandbox.files().exists("b.txt")).isTrue();
		}
	}

	@Test
	void shouldCleanupOnCloseShouldReturnTrue() {
		try (DockerSandbox sandbox = DockerSandbox.builder()
			.image("ghcr.io/spring-ai-community/agents-runtime:latest")
			.build()) {
			// Docker containers always clean up
			assertThat(sandbox.shouldCleanupOnClose()).isTrue();
		}
	}

	@Test
	void fileSpecOfShouldCreateFileSpec() {
		FileSpec spec = FileSpec.of("path/to/file.txt", "content");

		assertThat(spec.path()).isEqualTo("path/to/file.txt");
		assertThat(spec.content()).isEqualTo("content");
	}

}
