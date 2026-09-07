package com.brandempiricism.etocrm.commons.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brandempiricism.etocrm.commons.DiagnosticContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {
    private static final String TRACE = "1234567890abcdef1234567890abcdef";
    private static final String PARENT = "1234567890abcdef";
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();
    @AfterEach void clear() { MDC.clear(); }

    @Test void requestAndBusinessIdsAndUnsampledTracePropagateWithoutLeakingPreviousTenant() throws Exception {
        MDC.setContextMap(Map.of("tenantId", UUID.randomUUID().toString(), "worker", "before"));
        var previous = MDC.getCopyOfContextMap();
        var request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-" + TRACE + "-" + PARENT + "-00");
        request.addHeader("X-Request-Id", "request-123");
        request.addHeader("X-Business-Transaction-Id", "workflow-123");
        var response = new MockHttpServletResponse();
        var tenant = UUID.randomUUID();
        try (var logs = new LogCapture()) {
            filter.doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("tenantId")).isNull();
                assertThat(MDC.get("traceId")).isEqualTo(TRACE);
                assertThat(MDC.get("parentSpanId")).isEqualTo(PARENT);
                assertThat(MDC.get("spanId")).isNotEqualTo(PARENT);
                DiagnosticContext.verifiedIdentity("user-123", tenant);
            });
            var event = logs.events("http.request.completed").getFirst();
            assertThat(event.path("tenantId").asText()).isEqualTo(tenant.toString());
            assertThat(event.path("businessTransactionId").asText()).isEqualTo("workflow-123");
            assertThat(response.getHeader("traceparent")).startsWith("00-" + TRACE + "-").endsWith("-00");
            assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-123");
            assertThat(response.getHeader("X-Business-Transaction-Id")).isEqualTo("workflow-123");
        }
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previous);
    }

    @Test void invalidAndDuplicatedHeadersGenerateFreshContext() throws Exception {
        for (var trace : new String[] {"00-" + "0".repeat(32) + "-" + PARENT + "-01",
                "00-" + TRACE + "-" + "0".repeat(16) + "-01", "ff-" + TRACE + "-" + PARENT + "-01", "bad\nvalue"}) {
            var request = new MockHttpServletRequest();
            request.addHeader("traceparent", trace);
            request.addHeader("X-Request-Id", "first");
            request.addHeader("X-Request-Id", "second");
            request.addHeader("X-Business-Transaction-Id", "bad value");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {
                assertThat(MDC.get("traceId")).isNotEqualTo(TRACE).doesNotMatch("0+");
                assertThat(MDC.get("parentSpanId")).isNull();
            });
            assertThat(response.getHeader("X-Request-Id")).isNotIn("first", "second").hasSize(36);
            assertThat(response.getHeader("X-Business-Transaction-Id")).isEqualTo(response.getHeader("X-Request-Id"));
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test void failureRestoresWorkerContextAndLogsOneRedactedOutcome() {
        MDC.put("worker", "before");
        try (var logs = new LogCapture()) {
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("customer-canary"); }))
                .isInstanceOf(IllegalStateException.class);
            assertThat(logs.events("http.request.completed")).hasSize(1);
            assertThat(logs.events("http.request.completed").getFirst().path("httpStatus").asInt()).isEqualTo(500);
            assertThat(logs.output()).doesNotContain("customer-canary");
        }
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("worker", "before"));
    }
}
