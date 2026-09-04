package com.github.anicmv.telegrambot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.github.anicmv.telegrambot",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    @ArchTest
    static final ArchRule modelShouldNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..dispatcher..",
                    "..handler..",
                    "..service..",
                    "..gateway..",
                    "..messenger..",
                    "..config.."
            );

    @ArchTest
    static final ArchRule businessLayersShouldNotDependOnGateway = noClasses()
            .that().resideInAnyPackage("..dispatcher..", "..handler..")
            .should().dependOnClassesThat().resideInAPackage("..gateway..");

    @ArchTest
    static final ArchRule dispatcherShouldNotDependOnService = noClasses()
            .that().resideInAPackage("..dispatcher..")
            .should().dependOnClassesThat().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule longPollingShouldNotDependOnWebhook = noClasses()
            .that().resideInAPackage("..gateway.longpolling..")
            .should().dependOnClassesThat().resideInAPackage("..gateway.webhook..");

    @ArchTest
    static final ArchRule webhookShouldNotDependOnLongPolling = noClasses()
            .that().resideInAPackage("..gateway.webhook..")
            .should().dependOnClassesThat().resideInAPackage("..gateway.longpolling..")
            .allowEmptyShould(true); // webhook 控制器目前整体被注释，包为空
}
