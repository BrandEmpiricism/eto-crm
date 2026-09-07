package com.brandempiricism.etocrm.commons.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/** Captures encoded output immediately, before a worker restores its MDC. */
public final class LogCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final SafeLogEncoder encoder = new SafeLogEncoder();
    private final List<String> lines = new CopyOnWriteArrayList<>();

    public LogCapture() { start(); root.addAppender(this); }
    @Override protected void append(ILoggingEvent event) {
        lines.add(new String(encoder.encode(event), StandardCharsets.UTF_8));
    }
    public String output() { return String.join("", lines); }
    public List<JsonNode> events(String name) {
        var json = new ObjectMapper();
        return lines.stream().map(line -> {
            try { return json.readTree(line); }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }).filter(event -> name.equals(event.path("event.name").asText())).toList();
    }
    @Override public void close() { root.detachAppender(this); stop(); }
}
