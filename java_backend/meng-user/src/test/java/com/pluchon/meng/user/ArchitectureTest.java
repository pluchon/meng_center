package com.pluchon.meng.user;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void controllersDoNotAccessPersistenceOrProviderClients() {
        var classes = new ClassFileImporter().importPackages("com.pluchon.meng.user");
        noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..mapper..", "..client..", "..mq..")
            .check(classes);
        noClasses()
            .that()
            .resideInAPackage("..mapper..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..client..")
            .check(classes);
    }
}
