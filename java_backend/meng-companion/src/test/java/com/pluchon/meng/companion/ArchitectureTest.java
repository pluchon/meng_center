package com.pluchon.meng.companion;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

// 对外入口不越层，服务间只能使用公开契约
class ArchitectureTest {

    @Test
    void domainBoundaries() {
        var classes = new ClassFileImporter().importPackages(
            "com.pluchon.meng.companion"
        );
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
        noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc..")
            .check(classes);
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.pluchon.meng.user..",
                "com.pluchon.meng.content..",
                "com.pluchon.meng.growth..",
                "com.pluchon.meng.im.."
            )
            .check(classes);
    }
}
