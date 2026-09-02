package com.brandempiricism.etocrm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    private final ApplicationModules modules = ApplicationModules.of(EtoCrmApplication.class);
    @Test void verifiesModuleBoundaries() { modules.verify(); }
}

