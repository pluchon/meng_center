package com.pluchon.meng.im;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

// 消息域不依赖其他服务的实体或业务实现
class ArchitectureTest {

    @Test
    void domainAndLayersRemainIndependent() {
        var classes = new ClassFileImporter().importPackages(
            "com.pluchon.meng.im"
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
            .resideInAPackage("com.pluchon.meng.im..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.pluchon.meng.user..",
                "com.pluchon.meng.content..",
                "com.pluchon.meng.growth.."
            )
            .check(classes);
    }
}
