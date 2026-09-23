package com.sumit.movieticketbookingsystem;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(MovieBookingApplication.class);

    // Fails when a module uses another module's internal package or modules depend on each other in a cycle.
    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    // Module diagrams and canvases end up in target/spring-modulith-docs.
    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
