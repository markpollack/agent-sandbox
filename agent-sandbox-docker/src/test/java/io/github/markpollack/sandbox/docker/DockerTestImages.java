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

/**
 * Container images used by this module's tests.
 *
 * <p>
 * <strong>Test fixture only.</strong> Nothing here is shipped, endorsed as an application
 * runtime, or maintained by this project. {@code agent-sandbox-docker} has no default
 * image: callers select and own the image they run. This constant exists so the test
 * suite has something deterministic to run against.
 * </p>
 */
final class DockerTestImages {

	/**
	 * Minimal fixture supplying exactly what the TCK exercises: {@code bash}, GNU
	 * coreutils, and GNU findutils (the file listing uses {@code find -printf}, which
	 * BusyBox does not implement). No agent CLI, JDK, Go toolchain, or Maven is needed.
	 *
	 * <p>
	 * Pinned to the {@code ubuntu:24.04} multi-architecture index digest, so the
	 * reference is immutable while Docker still selects the right platform manifest on
	 * amd64 and arm64 developer machines alike.
	 * </p>
	 */
	static final String MINIMAL_POSIX = "ubuntu@sha256:561618e2c15bf2397621dd04f96926663a3b5616c189cf7e38db7e82f5c538ea";

	private DockerTestImages() {
	}

}
