package com.elibrary.lending.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 固化 tech-mvp.md 6.3 依赖规则：单向依赖、端口隔离、领域纯净。 */
@AnalyzeClasses(packages = "com.elibrary", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_layers_are_pure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "org.apache.ibatis..", "jakarta..")
                    .as("domain 层不得依赖 Spring/MyBatis/Jakarta");

    @ArchTest
    static final ArchRule catalog_does_not_depend_on_account =
            noClasses()
                    .that().resideInAPackage("com.elibrary.catalog..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.elibrary.account..")
                    .as("catalog 不得依赖 account（单向依赖）");

    @ArchTest
    static final ArchRule account_does_not_depend_on_lending =
            noClasses()
                    .that().resideInAPackage("com.elibrary.account..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.elibrary.lending..")
                    .as("account 不得依赖 lending（单向依赖）");

    @ArchTest
    static final ArchRule catalog_does_not_depend_on_lending =
            noClasses()
                    .that().resideInAPackage("com.elibrary.catalog..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.elibrary.lending..")
                    .as("catalog 不得依赖 lending（单向依赖）");

    @ArchTest
    static final ArchRule api_layers_depend_on_application_not_infrastructure =
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .as("api 层不得直接依赖 infrastructure（经 application 端口）");
}
