package com.brandempiricism.etocrm.capabilities.catalog;
import jakarta.persistence.*;import java.util.UUID;
@Entity @Table(name="capability") class CapabilityEntity{@Id UUID id;String name;String description;boolean active;protected CapabilityEntity(){}}
