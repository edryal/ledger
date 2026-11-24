package org.codeberg.edryal.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    static final ApplicationModules modules = ApplicationModules.of(LedgerApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // Generates PlantUML diagrams + module canvases into target/spring-modulith-docs
        new Documenter(modules).writeDocumentation();
    }
}
