package com.brandempiricism.etocrm.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.brandempiricism.etocrm.commons.DiagnosticContext;
import com.brandempiricism.etocrm.commons.DiagnosticEvents;
import com.brandempiricism.etocrm.commons.DiagnosticEvents.Event;
import com.brandempiricism.etocrm.commons.DiagnosticEvents.Outcome;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class SafeLogEncoderTest {
    @AfterEach void clear() { MDC.clear(); }

    @Test void levelsCannotExposeMessagesArgumentsPayloadsHeadersOrExceptionText() {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("canary.headers.payloads");
        var failure = new IllegalStateException("password-canary", new RuntimeException("host-canary"));
        failure.addSuppressed(new RuntimeException("cookie-canary"));
        for (var level : new Level[] {Level.INFO, Level.DEBUG, Level.TRACE}) {
            var event = new LoggingEvent(getClass().getName(), logger, level,
                "Authorization=Bearer token-canary body={} jdbc:postgresql://host-canary/db", failure,
                new Object[] {"customer-note-canary"});
            event.setMDCPropertyMap(Map.of("Authorization", "token-canary", "customer", "note-canary",
                "databaseRouteId", "jdbc:postgresql://host-canary", "requestId", "bad\nsecret-canary"));
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("password", "password-canary"));
            for (String format : new String[] {"json", "text"}) {
                var encoder = new SafeLogEncoder();
                encoder.setFormat(format);
                var output = new String(encoder.encode(event), StandardCharsets.UTF_8);
                assertThat(output).doesNotContain("canary", "jdbc:", "Authorization", "password", "cookie")
                    .contains("java.lang.IllegalStateException", "java.lang.RuntimeException", "frames", level.toString());
            }
        }
    }

    @Test void fixedEventsKeepVerifiedContextAndOnlyLogicalDatabaseIdentity() {
        var tenant = UUID.randomUUID();
        try (var logs = new LogCapture(); var context = DiagnosticContext.start(null)) {
            DiagnosticContext.verifiedIdentity("opaque-user-123", tenant);
            MDC.put("databaseRouteId", "jdbc:postgresql://hidden-host/db");
            DiagnosticEvents.finished(Event.ROUTE_FAILED, Outcome.failure, System.nanoTime(), 503,
                new IllegalStateException("secret-canary"));
            var event = logs.events("tenant.database.failed").getFirst();
            assertThat(event.path("event.outcome").asText()).isEqualTo("failure");
            assertThat(event.path("httpStatus").asInt()).isEqualTo(503);
            assertThat(event.path("durationMs").asLong()).isNotNegative();
            assertThat(event.path("tenantId").asText()).isEqualTo(tenant.toString());
            assertThat(event.path("databaseRouteId").asText()).isEqualTo("tenant:" + tenant);
            assertThat(event.path("actorId").asText()).isEqualTo("opaque-user-123");
            assertThat(event.path("traceId").asText()).hasSize(32);
            assertThat(logs.output()).doesNotContain("hidden-host", "secret-canary");
        }
    }

    @Test void nonOpaqueSubjectsRemainCorrelatableWithoutRevealingPersonalData() {
        try (var logs = new LogCapture(); var context = DiagnosticContext.start(null)) {
            DiagnosticContext.verifiedIdentity("personal-canary@example.test", null);
            DiagnosticEvents.emit(Event.AUTHENTICATED, Outcome.success);
            DiagnosticEvents.emit(Event.AUTHENTICATED, Outcome.success);
            var events = logs.events("security.authentication.accepted");
            assertThat(events.get(0).path("actorId").asText()).startsWith("sha256:").hasSize(71);
            assertThat(events.get(1).path("actorId")).isEqualTo(events.get(0).path("actorId"));
            assertThat(logs.output()).doesNotContain("personal-canary", "example.test");
        }
    }
}
