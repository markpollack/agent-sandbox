/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.sandbox.docker;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that an image is required and that the failure happens before Docker is
 * contacted.
 *
 * <p>
 * These run everywhere, with no Docker daemon and no {@code sandbox.infrastructure.test}
 * flag. That is the point: rejecting a missing image is a pure argument check, and if it
 * ever regressed into a container start the missing daemon would surface as a
 * {@code SandboxException} or a connection error instead of the
 * {@code IllegalArgumentException} asserted here.
 * </p>
 */
class DockerSandboxRequiredImageTest {

	private static final String EXPECTED_MESSAGE = "A Docker image is required";

	@Test
	void builderWithoutAnImageFailsBeforeContactingDocker() {
		assertThatThrownBy(() -> DockerSandbox.builder().build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(EXPECTED_MESSAGE)
			.hasMessageContaining("no default image");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "\t", "\n", "   " })
	void builderRejectsABlankImage(String blank) {
		assertThatThrownBy(() -> DockerSandbox.builder().image(blank).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(EXPECTED_MESSAGE);
	}

	@Test
	void builderRejectsANullImage() {
		assertThatThrownBy(() -> DockerSandbox.builder().image(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(EXPECTED_MESSAGE);
	}

	@Test
	void singleArgumentConstructorRejectsANullImage() {
		assertThatThrownBy(() -> new DockerSandbox(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(EXPECTED_MESSAGE);
	}

	@Test
	void twoArgumentConstructorRejectsABlankImage() {
		assertThatThrownBy(() -> new DockerSandbox("  ", List.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(EXPECTED_MESSAGE);
	}

	@Test
	void theFailureTellsTheCallerWhatToDo() {
		assertThatThrownBy(() -> DockerSandbox.builder().build())
			// names the method to call...
			.hasMessageContaining("DockerSandbox.builder().image(")
			// ...points at digests for production...
			.hasMessageContaining("immutable digest")
			// ...and states what the image has to provide.
			.hasMessageContaining("bash, GNU coreutils, and GNU findutils");
	}

}
