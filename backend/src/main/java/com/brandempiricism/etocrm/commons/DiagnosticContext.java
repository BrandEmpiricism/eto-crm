package com.brandempiricism.etocrm.commons;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Correlation only: a captured context never grants identity or tenant authorization. */
public final class DiagnosticContext implements AutoCloseable {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern TRACE_PARENT = Pattern.compile("00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, String> previous = MDC.getCopyOfContextMap();

    private DiagnosticContext() {}

    public static DiagnosticContext start(Correlation parent) {
        var scope = new DiagnosticContext();
        MDC.clear();
        String request = safe(parent == null ? null : parent.requestId(), UUID.randomUUID().toString());
        MDC.put("requestId", request);
        MDC.put("businessTransactionId", safe(parent == null ? null : parent.businessTransactionId(), request));
        var matcher = TRACE_PARENT.matcher(parent == null || parent.traceparent() == null ? "" : parent.traceparent());
        if (matcher.matches() && !matcher.group(1).equals("0".repeat(32)) && !matcher.group(2).equals("0".repeat(16))) {
            MDC.put("traceId", matcher.group(1));
            MDC.put("parentSpanId", matcher.group(2));
            MDC.put("traceFlags", matcher.group(3));
        } else {
            MDC.put("traceId", randomHex(16));
            MDC.put("traceFlags", "00");
        }
        MDC.put("spanId", randomHex(8));
        return scope;
    }

    public static DiagnosticContext forTenant(UUID tenant, String actor) {
        var scope = new DiagnosticContext();
        verifiedIdentity(actor, tenant);
        return scope;
    }

    public static void verifiedIdentity(String actor, UUID tenant) {
        if (actor != null) MDC.put("actorId", actor);
        if (tenant != null) {
            MDC.put("tenantId", tenant.toString());
            MDC.put("databaseRouteId", "tenant:" + tenant);
        }
    }

    public static Correlation capture() {
        return new Correlation(MDC.get("requestId"), traceparent(), MDC.get("businessTransactionId"));
    }

    public static String traceparent() {
        return MDC.get("traceId") == null || MDC.get("spanId") == null ? null
            : "00-" + MDC.get("traceId") + "-" + MDC.get("spanId") + "-" + MDC.get("traceFlags");
    }

    @Override public void close() { restore(previous); }

    public static void restore(Map<String, String> context) {
        if (context == null) MDC.clear(); else MDC.setContextMap(context);
    }

    private static String safe(String candidate, String fallback) {
        return candidate != null && SAFE_ID.matcher(candidate).matches() ? candidate : fallback;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        do { RANDOM.nextBytes(value); } while (java.util.Arrays.equals(value, new byte[bytes]));
        return HexFormat.of().formatHex(value);
    }

    public record Correlation(String requestId, String traceparent, String businessTransactionId) {}
}
