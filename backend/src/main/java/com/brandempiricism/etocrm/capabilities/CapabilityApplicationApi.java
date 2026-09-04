package com.brandempiricism.etocrm.capabilities;

import java.util.List;
import java.util.UUID;

public interface CapabilityApplicationApi {
    Capability get(UUID id);
    List<Capability> list();
    record Capability(UUID id, String name, String description, boolean active) {}
}
