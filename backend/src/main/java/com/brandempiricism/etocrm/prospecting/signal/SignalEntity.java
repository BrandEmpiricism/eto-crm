package com.brandempiricism.etocrm.prospecting.signal;
import jakarta.persistence.*;import java.time.*;import java.util.UUID;
@Entity @Table(name="prospect_signal")
class SignalEntity{@Id UUID id;UUID accountId;String source;LocalDate observedOn;String observedFact;String assumption;Instant createdAt;String createdBy;protected SignalEntity(){}SignalEntity(UUID id,UUID accountId,String source,LocalDate observedOn,String observedFact,String assumption,Instant now,String actor){this.id=id;this.accountId=accountId;this.source=source;this.observedOn=observedOn;this.observedFact=observedFact;this.assumption=assumption;this.createdAt=now;this.createdBy=actor;}}
