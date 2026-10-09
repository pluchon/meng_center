package com.pluchon.meng.common.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class CommonCoreArchitectureTest {

    @Test
    void coreDoesNotDependOnProjectModulesOrWebAndDatabaseFrameworks() {
        var classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.pluchon.meng.common.core");
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.pluchon.meng.api..",
                "com.pluchon.meng.user..",
                "com.pluchon.meng.gateway..",
                "com.pluchon.meng.content..",
                "com.pluchon.meng.im..",
                "com.pluchon.meng.growth..",
                "com.pluchon.meng.common.web..",
                "org.springframework..",
                "jakarta.servlet..",
                "com.baomidou..",
                "org.apache.ibatis.."
            )
            .check(classes);
    }
}
