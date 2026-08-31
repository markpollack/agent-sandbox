/*
 * Copyright (c) 2024 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */
package io.github.markpollack.sandbox;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests how {@link LocalSandbox} hands environment variables to the process executor.
 *
 * <p>
 * zt-exec logs its environment map -- names and values -- at DEBUG, and logback defaults
 * to DEBUG when a consumer ships no configuration. So whatever LocalSandbox puts in that
 * map is liable to end up in an ordinary consumer's log file. It must therefore contain
 * only the caller's own overrides, never a copy of the host environment.
 * </p>
 */
class LocalSandboxEnvironmentTest {

	private static final String EXECUTOR_LOGGER = "org.zeroturnaround.exec.ProcessExecutor";

	private ch.qos.logback.classic.Logger executorLogger;

	private ListAppender<ILoggingEvent> captured;

	private Level originalLevel;

	@BeforeEach
	void captureExecutorLog() {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		executorLogger = context.getLogger(EXECUTOR_LOGGER);
		originalLevel = executorLogger.getLevel();
		executorLogger.setLevel(Level.DEBUG);

		captured = new ListAppender<>();
		captured.setContext(context);
		captured.start();
		executorLogger.addAppender(captured);
	}

	@AfterEach
	void releaseExecutorLog() {
		executorLogger.detachAppender(captured);
		captured.stop();
		executorLogger.setLevel(originalLevel);
	}

	@Test
	void execDoesNotLogTheHostEnvironment() {
		try (Sandbox sandbox = LocalSandbox.builder().tempDirectory("env-log-").build()) {
			sandbox.exec(ExecSpec.builder()
				.command("printenv", "SANDBOX_TEST_VAR")
				.env(Map.of("SANDBOX_TEST_VAR", "declared-by-caller"))
				.timeout(Duration.ofSeconds(30))
				.build());
		}

		List<String> messages = captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

		// The caller's own variable is expected in the executor's environment...
		assertThat(messages).anyMatch(message -> message.contains("SANDBOX_TEST_VAR"));
		// ...but nothing inherited from the host, which is where secrets live.
		assertThat(messages).noneMatch(message -> message.contains("PATH="));
		for (String hostVariable : System.getenv().keySet()) {
			assertThat(messages).noneMatch(message -> message.contains(hostVariable + "="));
		}
	}

	@Test
	void execStillInheritsTheHostEnvironment() {
		try (Sandbox sandbox = LocalSandbox.builder().tempDirectory("env-inherit-").build()) {
			// PATH is not declared on the spec, so the process can only see it by
			// inheritance. Declaring an unrelated variable forces the environment-applying
			// branch to run.
			ExecResult result = sandbox.exec(ExecSpec.builder()
				.command("printenv", "PATH")
				.env(Map.of("SANDBOX_TEST_VAR", "declared-by-caller"))
				.timeout(Duration.ofSeconds(30))
				.build());

			assertThat(result.success()).isTrue();
			assertThat(result.stdout().trim()).isEqualTo(System.getenv("PATH"));
		}
	}

	@Test
	void toStringOfASpecDoesNotRevealEnvironmentValues() {
		ExecSpec spec = ExecSpec.builder()
			.command("deploy")
			.env(Map.of("E2B_API_KEY", "e2b_thisisnotarealkey"))
			.build();

		assertThat(spec.toString()).contains("E2B_API_KEY").doesNotContain("e2b_thisisnotarealkey");
	}

}
