package com.stockpulse;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Layer boundaries for the flat controller/service/entity package layout. */
@AnalyzeClasses(packages = "com.stockpulse", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule controllersDoNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..messaging..", "..integration.llm..");

  @ArchTest
  static final ArchRule entitiesDoNotDependOnApplicationLayers =
      noClasses()
          .that()
          .resideInAPackage("..entity..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..", "..messaging..", "..integration..");

  @ArchTest
  static final ArchRule onlyAiStrategiesUseTheLlmGateway =
      noClasses()
          .that()
          .resideOutsideOfPackages("..recommendation.ai..", "..integration.llm..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..integration.llm..")
          .allowEmptyShould(true);
}
