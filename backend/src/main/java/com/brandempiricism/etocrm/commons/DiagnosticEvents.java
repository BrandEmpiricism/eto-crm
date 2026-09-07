package com.brandempiricism.etocrm.commons;

import java.util.Arrays;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fixed event vocabulary and identifiers only; customer payloads never enter this API. */
public final class DiagnosticEvents {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DiagnosticEvents.class);
    private DiagnosticEvents() {}

    public enum Event {
        REQUEST_COMPLETED("http.request.completed"),
        AUTHENTICATED("security.authentication.accepted"),
        AUTHENTICATION_REJECTED("security.authentication.rejected"),
        AUTHORIZATION_REJECTED("security.authorization.rejected"),
        MEMBERSHIP_ACCEPTED("security.membership.accepted"),
        MEMBERSHIP_REJECTED("security.membership.rejected"),
        MEMBERSHIP_UNAVAILABLE("security.membership.unavailable"),
        ROUTE_ACCEPTED("tenant.database.ready"),
        ROUTE_FAILED("tenant.database.failed"),
        JOB_COMPLETED("tenant.job.completed"),
        PROVISIONING_REGISTERED("tenant.provisioning.registered"),
        PROVISIONING_REPLAYED("tenant.provisioning.request_replayed"),
        PROVISIONING_STEP("tenant.provisioning.step_completed"),
        PROVISIONING_ACTIVATED("tenant.provisioning.activated"),
        PROVISIONING_FAILED("tenant.provisioning.failed"),
        MIGRATION_COMPLETED("tenant.migration.completed"),
        ACCOUNT_CREATED("account.created"),
        SIGNAL_RECORDED("signal.recorded"), MATCH_SAVED("capability_match.saved"),
        REQUEST_REJECTED("http.request.rejected"), REQUEST_FAILED("http.request.failed");

        public final String code;
        Event(String code) { this.code = code; }
    }
    public enum Outcome { success, rejected, failure }

    public static boolean knownEvent(String value) {
        return Arrays.stream(Event.values()).anyMatch(event -> event.code.equals(value));
    }

    public static void emit(Event event, Outcome outcome) { emit(event, outcome, null); }
    public static void emit(Event event, Outcome outcome, Throwable failure) {
        write(event, outcome, failure, null, null, null);
    }
    public static void finished(Event event, Outcome outcome, long started, int status, Throwable failure) {
        write(event, outcome, failure, Math.max(0, (System.nanoTime() - started) / 1_000_000), status, null);
    }
    public static void afterCommit(Event event, UUID aggregateId) {
        var context = MDC.getCopyOfContextMap();
        Runnable committed = () -> {
            var previous = MDC.getCopyOfContextMap();
            try {
                DiagnosticContext.restore(context);
                write(event, Outcome.success, null, null, null, aggregateId);
            } finally { DiagnosticContext.restore(previous); }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { committed.run(); }
            });
        } else committed.run();
    }

    private static void write(Event event, Outcome outcome, Throwable failure, Long duration, Integer status, UUID aggregate) {
        var log = switch (outcome) {
            case success -> LOG.atInfo(); case rejected -> LOG.atWarn(); case failure -> LOG.atError();
        };
        log.addKeyValue("event.name", event.code).addKeyValue("event.outcome", outcome.name());
        if (duration != null) log.addKeyValue("durationMs", duration);
        if (status != null && status >= 100) log.addKeyValue("httpStatus", status);
        if (aggregate != null) log.addKeyValue("aggregateId", aggregate.toString());
        if (failure != null) log.setCause(failure);
        log.log(event.code);
    }
}
