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

import java.util.List;

import io.github.markpollack.sandbox.FileEntry;
import io.github.markpollack.sandbox.FileSpec;
import io.github.markpollack.sandbox.Sandbox;
import io.github.markpollack.sandbox.SandboxFiles;

/**
 * E2B implementation of {@link SandboxFiles}.
 *
 * <p>
 * Provides file operations for {@link E2BSandbox} using the E2B envd service.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class E2BSandboxFiles implements SandboxFiles {

	private final E2BSandbox sandbox;

	private final E2BEnvdClient envdClient;

	private final String workDir;

	E2BSandboxFiles(E2BSandbox sandbox, E2BEnvdClient envdClient, String workDir) {
		this.sandbox = sandbox;
		this.envdClient = envdClient;
		this.workDir = workDir;
	}

	@Override
	public SandboxFiles create(String relativePath, String content) {
		String fullPath = resolvePath(relativePath);
		// Ensure parent directory exists
		String parentDir = getParentPath(fullPath);
		if (parentDir != null) {
			envdClient.makeDir(parentDir);
		}
		envdClient.writeFile(fullPath, content);
		return this;
	}

	@Override
	public SandboxFiles createDirectory(String relativePath) {
		String fullPath = resolvePath(relativePath);
		envdClient.makeDir(fullPath);
		return this;
	}

	@Override
	public SandboxFiles setup(List<FileSpec> files) {
		for (FileSpec file : files) {
			create(file.path(), file.content());
		}
		return this;
	}

	@Override
	public String read(String relativePath) {
		String fullPath = resolvePath(relativePath);
		return envdClient.readFile(fullPath);
	}

	@Override
	public boolean exists(String relativePath) {
		String fullPath = resolvePath(relativePath);
		return envdClient.exists(fullPath);
	}

	@Override
	public List<FileEntry> list(String relativePath) {
		return list(relativePath, 1);
	}

	@Override
	public List<FileEntry> list(String relativePath, int maxDepth) {
		String fullPath = resolvePath(relativePath);
		return envdClient.listFiles(fullPath, maxDepth);
	}

	@Override
	public SandboxFiles delete(String relativePath) {
		return delete(relativePath, false);
	}

	@Override
	public SandboxFiles delete(String relativePath, boolean recursive) {
		String fullPath = resolvePath(relativePath);
		envdClient.remove(fullPath);
		return this;
	}

	@Override
	public Sandbox and() {
		return sandbox;
	}

	private String resolvePath(String relativePath) {
		if (relativePath.startsWith("/")) {
			return relativePath;
		}
		if (".".equals(relativePath)) {
			return workDir;
		}
		return workDir + "/" + relativePath;
	}

	private String getParentPath(String path) {
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash > 0) {
			return path.substring(0, lastSlash);
		}
		return null;
	}

}
