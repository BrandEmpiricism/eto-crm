package com.brandempiricism.etocrm.commons.observability;

import com.brandempiricism.etocrm.commons.DiagnosticContext;
import com.brandempiricism.etocrm.commons.DiagnosticEvents;
import com.brandempiricism.etocrm.commons.DiagnosticEvents.Event;
import com.brandempiricism.etocrm.commons.DiagnosticEvents.Outcome;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String BUSINESS_TRANSACTION_HEADER = "X-Business-Transaction-Id";

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var parent = new DiagnosticContext.Correlation(single(request, REQUEST_ID_HEADER), single(request, "traceparent"),
            single(request, BUSINESS_TRANSACTION_HEADER));
        long started = System.nanoTime();
        try (var scope = DiagnosticContext.start(parent)) {
            response.setHeader(REQUEST_ID_HEADER, MDC.get("requestId"));
            response.setHeader(BUSINESS_TRANSACTION_HEADER, MDC.get("businessTransactionId"));
            response.setHeader("traceparent", DiagnosticContext.traceparent());
            try {
                chain.doFilter(request, response);
            } catch (ServletException | IOException | RuntimeException failure) {
                DiagnosticEvents.finished(Event.REQUEST_COMPLETED, Outcome.failure, started, 500, failure);
                throw failure;
            }
            int status = response.getStatus();
            DiagnosticEvents.finished(Event.REQUEST_COMPLETED,
                status >= 500 ? Outcome.failure : status >= 400 ? Outcome.rejected : Outcome.success, started, status, null);
        }
    }

    private static String single(HttpServletRequest request, String name) {
        var values = java.util.Collections.list(request.getHeaders(name));
        return values.size() == 1 ? values.getFirst() : null;
    }
}
