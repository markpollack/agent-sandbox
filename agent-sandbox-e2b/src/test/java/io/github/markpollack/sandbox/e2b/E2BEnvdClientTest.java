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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the argv-to-shell rendering that {@link E2BEnvdClient} uses.
 *
 * <p>
 * E2B runs commands through a login shell, so the argument vector has to survive a round
 * trip through shell word splitting. If it does not, the same {@code ExecSpec} means one
 thing on {@code LocalSandbox} and another on {@code E2BSandbox}, and caller-supplied
 * arguments become shell code.
 * </p>
 */
class E2BEnvdClientTest {

	@Test
	void quotesEachArgumentAsOneWord() {
		assertThat(E2BEnvdClient.toShellCommand(List.of("echo", "hello"))).isEqualTo("'echo' 'hello'");
	}

	@Test
	void keepsWhitespaceInsideASingleArgument() {
		assertThat(E2BEnvdClient.toShellCommand(List.of("touch", "my file.txt"))).isEqualTo("'touch' 'my file.txt'");
	}

	@Test
	void doesNotLetTheShellExpandAnArgument() {
		assertThat(E2BEnvdClient.toShellCommand(List.of("echo", "$HOME", "*", "`id`")))
			.isEqualTo("'echo' '$HOME' '*' '`id`'");
	}

	@Test
	void neutralizesACommandSeparatorInAnArgument() {
		assertThat(E2BEnvdClient.toShellCommand(List.of("echo", "a; rm -rf /"))).isEqualTo("'echo' 'a; rm -rf /'");
	}

	@Test
	void escapesAnEmbeddedSingleQuoteRatherThanClosingTheLiteral() {
		// The classic break-out attempt: a quote that would otherwise end the literal
		// and leave the rest of the argument running as shell code.
		assertThat(E2BEnvdClient.toShellCommand(List.of("echo", "it's'; id #")))
			.isEqualTo("'echo' 'it'\\''s'\\''; id #'");
	}

	@Test
	void rendersAnEmptyArgumentAsAnEmptyWord() {
		assertThat(E2BEnvdClient.toShellCommand(List.of("echo", ""))).isEqualTo("'echo' ''");
	}

}
