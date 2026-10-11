package com.pluchon.meng.growth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void layers() {
        var classes = new ClassFileImporter().importPackages("com.pluchon.meng.growth");
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
                .resideInAnyPackage("..service..", "..controller..", "..client..")
                .check(classes);
    }
}
