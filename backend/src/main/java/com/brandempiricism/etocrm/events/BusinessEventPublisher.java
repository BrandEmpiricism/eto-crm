package com.brandempiricism.etocrm.events;

/** Records a fact in the caller's tenant transaction; this port does not deliver it to consumers. */
public interface BusinessEventPublisher {
    void record(BusinessEvent event, String actor);
}
