package com.shardbound;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The game engine stays framework-free (P-02): it can be unit-tested, run in
 * tools and the balance simulator, and moved to another framework without
 * touching a rule. Only web/, persistence/ and config/ may use Spring, Jakarta
 * or JDBC.
 */
class ArchitectureTest {

    private static final String[] ENGINE_PACKAGES = {
            "com.shardbound.engine..", "com.shardbound.combat..", "com.shardbound.quest..",
            "com.shardbound.rules..", "com.shardbound.story..", "com.shardbound.item..",
            "com.shardbound.model..", "com.shardbound.ds..", "com.shardbound.event..",
            "com.shardbound.achievement..", "com.shardbound.content..", "com.shardbound.exception..",
            "com.shardbound.save..", "com.shardbound.view..", "com.shardbound.tools.."};

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.shardbound");
    }

    @Test
    void engineDoesNotDependOnFrameworksOrJdbc() {
        ArchRule rule = noClasses().that().resideInAnyPackage(ENGINE_PACKAGES)
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "javax.sql..",
                        "io.swagger..", "org.springdoc..");
        rule.check(classes);
    }

    @Test
    void engineDoesNotReachIntoTheOuterLayers() {
        ArchRule rule = noClasses().that().resideInAnyPackage(ENGINE_PACKAGES)
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.shardbound.web..", "com.shardbound.persistence..", "com.shardbound.config..");
        rule.check(classes);
    }
}
