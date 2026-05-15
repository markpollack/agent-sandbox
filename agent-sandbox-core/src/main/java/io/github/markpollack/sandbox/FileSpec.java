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

/**
 * Specification for a file to be created in a sandbox workspace.
 *
 * @param path relative path within the sandbox working directory
 * @param content file content as a string
 * @author Mark Pollack
 * @since 0.1.0
 */
public record FileSpec(String path, String content) {

	/**
	 * Create a file specification.
	 * @param path relative path within the sandbox
	 * @param content file content
	 * @return a new FileSpec
	 */
	public static FileSpec of(String path, String content) {
		return new FileSpec(path, content);
	}

}
