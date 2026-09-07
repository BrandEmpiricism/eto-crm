package com.brandempiricism.etocrm.events;

import java.util.Objects;
import java.util.UUID;

/** Minimal version-one fact contracts: arbitrary customer content cannot enter the payload. */
public sealed interface BusinessEvent {
    record AccountCreated(UUID accountId) implements BusinessEvent {
        public AccountCreated { Objects.requireNonNull(accountId); }
    }

    record SignalRecorded(UUID signalId, UUID accountId) implements BusinessEvent {
        public SignalRecorded {
            Objects.requireNonNull(signalId);
            Objects.requireNonNull(accountId);
        }
    }

    record CapabilityMatchSaved(UUID matchId, UUID accountId, UUID signalId, UUID capabilityId,
                                MatchState status) implements BusinessEvent {
        public CapabilityMatchSaved {
            Objects.requireNonNull(matchId);
            Objects.requireNonNull(accountId);
            Objects.requireNonNull(signalId);
            Objects.requireNonNull(capabilityId);
            Objects.requireNonNull(status);
        }
    }

    enum MatchState { DRAFT, ACTIVE }
}
