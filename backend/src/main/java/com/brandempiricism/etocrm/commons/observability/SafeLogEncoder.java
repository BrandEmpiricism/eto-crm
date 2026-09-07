package com.brandempiricism.etocrm.commons.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.encoder.EncoderBase;
import com.brandempiricism.etocrm.commons.DiagnosticEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The sole output boundary: arbitrary messages, arguments, MDC, and exception text are omitted. */
public class SafeLogEncoder extends EncoderBase<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> IDS = Set.of("requestId", "businessTransactionId");
    private String format = "json";

    public void setFormat(String format) { this.format = format; }
    @Override public byte[] headerBytes() { return new byte[0]; }
    @Override public byte[] footerBytes() { return new byte[0]; }

    @Override public byte[] encode(ILoggingEvent event) {
        var record = new LinkedHashMap<String, Object>();
        record.put("@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        record.put("log.level", event.getLevel().toString());
        record.put("service.name", "eto-crm");
        boolean trusted = DiagnosticEvents.class.getName().equals(event.getLoggerName())
            && DiagnosticEvents.knownEvent(event.getMessage());
        record.put("event.name", trusted ? event.getMessage() : "framework.diagnostic");
        record.put("event.outcome", "unknown");
        copyContext(record, event.getMDCPropertyMap());
        if (trusted && event.getKeyValuePairs() != null) {
            for (var pair : event.getKeyValuePairs()) {
                if (pair.key.equals("event.outcome") && pair.value instanceof String value
                        && Set.of("success", "rejected", "failure").contains(value)) record.put(pair.key, value);
                if (pair.key.equals("durationMs") && pair.value instanceof Long value && value >= 0) record.put(pair.key, value);
                if (pair.key.equals("httpStatus") && pair.value instanceof Integer value && value >= 100 && value <= 599) record.put(pair.key, value);
                if (pair.key.equals("aggregateId") && pair.value instanceof String value && uuid(value)) record.put(pair.key, value);
            }
        }
        if (event.getThrowableProxy() != null) record.put("error", exception(event.getThrowableProxy()));
        try {
            String line = JSON.writeValueAsString(record);
            if ("text".equals(format)) line = record.get("@timestamp") + " " + record.get("log.level") + " "
                + record.get("event.name") + " " + line;
            return (line + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception failure) {
            return "{\"event.name\":\"logging.encoding.failed\",\"event.outcome\":\"failure\"}\n".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void copyContext(Map<String, Object> record, Map<String, String> context) {
        if (context == null) return;
        for (var key : IDS) {
            String value = context.get(key);
            if (value != null && value.matches("[A-Za-z0-9._:-]{1,120}")) record.put(key, value);
        }
        String actor = context.get("actorId");
        if (actor != null && !actor.isBlank()) record.put("actorId", actor.matches("[A-Za-z0-9._:-]{1,120}") ? actor : actorHash(actor));
        for (var key : Set.of("traceId", "spanId", "parentSpanId")) {
            String value = context.get(key);
            int length = key.equals("traceId") ? 32 : 16;
            if (value != null && value.matches("[0-9a-f]{" + length + "}") && !value.equals("0".repeat(length))) record.put(key, value);
        }
        String tenant = context.get("tenantId");
        if (uuid(tenant)) {
            record.put("tenantId", tenant);
            record.put("databaseRouteId", "tenant:" + tenant);
        }
    }

    private static Object exception(IThrowableProxy failure) {
        var chain = new ArrayList<Object>();
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("type", safeClass(failure.getClassName()));
            var frames = new ArrayList<String>();
            var stack = failure.getStackTraceElementProxyArray();
            if (stack != null) for (int i = 0; i < Math.min(stack.length, 32); i++) {
                var frame = stack[i].getStackTraceElement();
                frames.add(safeClass(frame.getClassName()) + "." + safeClass(frame.getMethodName()) + ":" + frame.getLineNumber());
            }
            entry.put("frames", frames);
            chain.add(entry);
        }
        return chain;
    }

    private static String safeClass(String value) {
        return value != null && value.matches("[A-Za-z0-9_.$<>]{1,250}") ? value : "redacted";
    }
    private static String actorHash(String actor) {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(actor.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean uuid(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
