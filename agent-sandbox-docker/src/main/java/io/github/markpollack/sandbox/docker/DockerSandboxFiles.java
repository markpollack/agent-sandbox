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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import io.github.markpollack.sandbox.FileEntry;
import io.github.markpollack.sandbox.FileSpec;
import io.github.markpollack.sandbox.FileType;
import io.github.markpollack.sandbox.Sandbox;
import io.github.markpollack.sandbox.SandboxException;
import io.github.markpollack.sandbox.SandboxFiles;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

/**
 * Docker container implementation of {@link SandboxFiles}.
 *
 * <p>
 * Provides file operations for {@link DockerSandbox} using container exec commands. Files
 * are created and read inside the running container.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class DockerSandboxFiles implements SandboxFiles {

	private final DockerSandbox sandbox;

	private final GenericContainer<?> container;

	DockerSandboxFiles(DockerSandbox sandbox, GenericContainer<?> container) {
		this.sandbox = sandbox;
		this.container = container;
	}

	@Override
	public SandboxFiles create(String relativePath, String content) {
		try {
			String fullPath = "/work/" + relativePath;

			// Create parent directories
			String parentDir = getParentPath(fullPath);
			if (parentDir != null) {
				ExecResult mkdirResult = container.execInContainer("mkdir", "-p", parentDir);
				if (mkdirResult.getExitCode() != 0) {
					throw new SandboxException("Failed to create parent directory: " + parentDir + " - "
							+ mkdirResult.getStderr() + mkdirResult.getStdout());
				}
			}

			// Write file content using printf to handle special characters and
			// multi-line content
			// We use printf %s to write the exact content without interpretation
			ExecResult writeResult = container.execInContainer("bash", "-c", "printf '%s' \"$1\" > \"$2\"", "bash",
					content, fullPath);

			if (writeResult.getExitCode() != 0) {
				throw new SandboxException("Failed to create file: " + relativePath + " - " + writeResult.getStderr());
			}
			return this;
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (Exception e) {
			throw new SandboxException("Failed to create file: " + relativePath, e);
		}
	}

	@Override
	public SandboxFiles createDirectory(String relativePath) {
		try {
			String fullPath = "/work/" + relativePath;
			ExecResult result = container.execInContainer("mkdir", "-p", fullPath);
			if (result.getExitCode() != 0) {
				throw new SandboxException("Failed to create directory: " + relativePath + " - " + result.getStderr());
			}
			return this;
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (Exception e) {
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
			String fullPath = "/work/" + relativePath;
			ExecResult result = container.execInContainer("cat", fullPath);
			if (result.getExitCode() != 0) {
				throw new SandboxException("Failed to read file: " + relativePath + " - " + result.getStderr());
			}
			return result.getStdout();
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (Exception e) {
			throw new SandboxException("Failed to read file: " + relativePath, e);
		}
	}

	@Override
	public boolean exists(String relativePath) {
		try {
			String fullPath = "/work/" + relativePath;
			ExecResult result = container.execInContainer("test", "-e", fullPath);
			return result.getExitCode() == 0;
		}
		catch (Exception e) {
			return false;
		}
	}

	@Override
	public List<FileEntry> list(String relativePath) {
		return list(relativePath, 1);
	}

	@Override
	public List<FileEntry> list(String relativePath, int maxDepth) {
		try {
			String fullPath = "/work/" + relativePath;

			// Check if path exists and is a directory
			ExecResult testResult = container.execInContainer("test", "-d", fullPath);
			if (testResult.getExitCode() != 0) {
				// Check if it exists at all
				ExecResult existsResult = container.execInContainer("test", "-e", fullPath);
				if (existsResult.getExitCode() != 0) {
					throw new SandboxException("Path does not exist: " + relativePath);
				}
				throw new SandboxException("Path is not a directory: " + relativePath);
			}

			// Use find command with maxdepth to list files
			// Output format: type|size|mtime|path
			// type: d for directory, f for file
			// Use stat to get size and mtime
			ExecResult findResult = container.execInContainer("bash", "-c", "find \"" + fullPath
					+ "\" -mindepth 1 -maxdepth " + maxDepth + " -printf '%y|%s|%T@|%p\\n' 2>/dev/null | sort");

			if (findResult.getExitCode() != 0) {
				throw new SandboxException(
						"Failed to list directory: " + relativePath + " - " + findResult.getStderr());
			}

			List<FileEntry> entries = new ArrayList<>();
			String output = findResult.getStdout();
			if (!output.isEmpty()) {
				for (String line : output.split("\n")) {
					if (line.trim().isEmpty()) {
						continue;
					}
					String[] parts = line.split("\\|", 4);
					if (parts.length >= 4) {
						FileType type = "d".equals(parts[0]) ? FileType.DIRECTORY : FileType.FILE;
						long size = type == FileType.DIRECTORY ? 0 : Long.parseLong(parts[1]);
						// parts[2] is epoch seconds with decimal, parse to Instant
						double epochSeconds = Double.parseDouble(parts[2]);
						Instant modifiedTime = Instant.ofEpochSecond((long) epochSeconds,
								(long) ((epochSeconds % 1) * 1_000_000_000));
						String absolutePath = parts[3];
						// Convert to relative path from workdir
						String path = absolutePath.startsWith("/work/") ? absolutePath.substring(6) : absolutePath;
						String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
						entries.add(new FileEntry(name, type, path, size, modifiedTime));
					}
				}
			}
			return entries;
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (Exception e) {
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
			String fullPath = "/work/" + relativePath;

			// Check if path exists
			ExecResult existsResult = container.execInContainer("test", "-e", fullPath);
			if (existsResult.getExitCode() != 0) {
				throw new SandboxException("Path does not exist: " + relativePath);
			}

			ExecResult deleteResult;
			if (recursive) {
				deleteResult = container.execInContainer("rm", "-rf", fullPath);
			}
			else {
				// For non-recursive, use rm for files and rmdir for directories
				ExecResult isDirResult = container.execInContainer("test", "-d", fullPath);
				if (isDirResult.getExitCode() == 0) {
					deleteResult = container.execInContainer("rmdir", fullPath);
				}
				else {
					deleteResult = container.execInContainer("rm", fullPath);
				}
			}

			if (deleteResult.getExitCode() != 0) {
				throw new SandboxException("Failed to delete: " + relativePath + " - " + deleteResult.getStderr());
			}
			return this;
		}
		catch (SandboxException e) {
			throw e;
		}
		catch (Exception e) {
			throw new SandboxException("Failed to delete: " + relativePath, e);
		}
	}

	@Override
	public Sandbox and() {
		return sandbox;
	}

	private String getParentPath(String path) {
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash > 0) {
			return path.substring(0, lastSlash);
		}
		return null;
	}

}
