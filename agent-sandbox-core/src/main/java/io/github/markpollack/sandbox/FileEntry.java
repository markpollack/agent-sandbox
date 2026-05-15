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

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an entry in a directory listing.
 *
 * <p>
 * Contains metadata about a file or directory within a sandbox workspace.
 * </p>
 *
 * @param name the file or directory name (without path)
 * @param type whether this is a file or directory
 * @param path the relative path from the sandbox working directory
 * @param size the file size in bytes (0 for directories)
 * @param modifiedTime the last modification time
 * @author Mark Pollack
 * @since 0.1.0
 */
public record FileEntry(String name, FileType type, String path, long size, Instant modifiedTime) {

	public FileEntry {
		Objects.requireNonNull(name, "name cannot be null");
		Objects.requireNonNull(type, "type cannot be null");
		Objects.requireNonNull(path, "path cannot be null");
		Objects.requireNonNull(modifiedTime, "modifiedTime cannot be null");
	}

	/**
	 * Checks if this entry is a regular file.
	 * @return true if this is a file, false if directory
	 */
	public boolean isFile() {
		return type == FileType.FILE;
	}

	/**
	 * Checks if this entry is a directory.
	 * @return true if this is a directory, false if file
	 */
	public boolean isDirectory() {
		return type == FileType.DIRECTORY;
	}

}
