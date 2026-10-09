package com.pluchon.meng.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void gatewayNeverPullsServletOrBusinessPersistence() {
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "jakarta.servlet..",
                "com.baomidou..",
                "com.pluchon.meng.user..",
                "com.pluchon.meng.common.web..",
                "java.sql.."
            )
            .check(new ClassFileImporter().importPackages("com.pluchon.meng.gateway"));
    }
}
