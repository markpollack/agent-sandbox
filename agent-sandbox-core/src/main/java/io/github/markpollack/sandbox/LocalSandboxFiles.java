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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Local filesystem implementation of {@link SandboxFiles}.
 *
 * <p>
 * Provides file operations for {@link LocalSandbox} using {@link java.nio.file.Files}.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class LocalSandboxFiles implements SandboxFiles {

	private final LocalSandbox sandbox;

	private final Path workDir;

	LocalSandboxFiles(LocalSandbox sandbox, Path workDir) {
		this.sandbox = sandbox;
		this.workDir = workDir;
	}

	@Override
	public SandboxFiles create(String relativePath, String content) {
		try {
			Path filePath = workDir.resolve(relativePath);
			// Create parent directories if needed
			Path parent = filePath.getParent();
			if (parent != null && !Files.exists(parent)) {
				Files.createDirectories(parent);
			}
			Files.writeString(filePath, content, StandardCharsets.UTF_8);
			return this;
		}
		catch (IOException e) {
			throw new SandboxException("Failed to create file: " + relativePath, e);
		}
	}

	@Override
	public SandboxFiles createDirectory(String relativePath) {
		try {
			Path dirPath = workDir.resolve(relativePath);
			Files.createDirectories(dirPath);
			return this;
		}
		catch (IOException e) {
			throw new SandboxException("Failed to create directory: " + relativePath, e);
		}
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
		try {
			Path filePath = workDir.resolve(relativePath);
			return Files.readString(filePath, StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new SandboxException("Failed to read file: " + relativePath, e);
		}
	}

	@Override
	public boolean exists(String relativePath) {
		Path path = workDir.resolve(relativePath);
		return Files.exists(path);
	}

	@Override
	public List<FileEntry> list(String relativePath) {
		return list(relativePath, 1);
	}

	@Override
	public List<FileEntry> list(String relativePath, int maxDepth) {
		try {
			Path dirPath = workDir.resolve(relativePath);
			if (!Files.exists(dirPath)) {
				throw new SandboxException("Path does not exist: " + relativePath);
			}
			if (!Files.isDirectory(dirPath)) {
				throw new SandboxException("Path is not a directory: " + relativePath);
			}

			List<FileEntry> entries = new ArrayList<>();
			try (Stream<Path> stream = Files.walk(dirPath, maxDepth)) {
				stream.filter(p -> !p.equals(dirPath)).forEach(p -> {
					try {
						String name = p.getFileName().toString();
						String path = workDir.relativize(p).toString();
						FileType type = Files.isDirectory(p) ? FileType.DIRECTORY : FileType.FILE;
						long size = Files.isDirectory(p) ? 0 : Files.size(p);
						Instant modifiedTime = Files.getLastModifiedTime(p).toInstant();
						entries.add(new FileEntry(name, type, path, size, modifiedTime));
					}
					catch (IOException e) {
						throw new SandboxException("Failed to read file attributes: " + p, e);
					}
				});
			}
			return entries;
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (IOException e) {
			throw new SandboxException("Failed to list directory: " + relativePath, e);
		}
	}

	@Override
	public SandboxFiles delete(String relativePath) {
		return delete(relativePath, false);
	}

	@Override
	public SandboxFiles delete(String relativePath, boolean recursive) {
		try {
			Path path = workDir.resolve(relativePath);
			if (!Files.exists(path)) {
				throw new SandboxException("Path does not exist: " + relativePath);
			}

			if (recursive && Files.isDirectory(path)) {
				// Walk in reverse order (deepest first) to delete contents before parent
				try (Stream<Path> stream = Files.walk(path)) {
					stream.sorted(Comparator.reverseOrder()).forEach(p -> {
						try {
							Files.delete(p);
						}
						catch (IOException e) {
							throw new SandboxException("Failed to delete: " + p, e);
						}
					});
				}
			}
			else {
				Files.delete(path);
			}
			return this;
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (IOException e) {
			throw new SandboxException("Failed to delete: " + relativePath, e);
		}
	}

	@Override
	public Sandbox and() {
		return sandbox;
	}

}
