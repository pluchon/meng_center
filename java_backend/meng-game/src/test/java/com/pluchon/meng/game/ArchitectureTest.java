package com.pluchon.meng.game;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void layers() {
        var classes = new ClassFileImporter().importPackages("com.pluchon.meng.game");
        noClasses()
                .that()
                .resideInAPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..mapper..")
                .check(classes);
        noClasses()
                .that()
                .resideInAPackage("..mapper..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..service..", "..controller..")
                .check(classes);
    }

    // 金币只在游戏服务里流转：不依赖任何其他业务域的契约，也不收发消息
    @Test
    void isolatedFromOtherDomains() {
        var classes = new ClassFileImporter().importPackages("com.pluchon.meng.game");
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.pluchon.meng.api..",
                        "com.pluchon.meng.user..",
                        "com.pluchon.meng.content..",
                        "com.pluchon.meng.growth..",
                        "com.pluchon.meng.im..",
                        "org.springframework.amqp..",
                        "org.springframework.cloud.openfeign..")
                .check(classes);
    }
}
