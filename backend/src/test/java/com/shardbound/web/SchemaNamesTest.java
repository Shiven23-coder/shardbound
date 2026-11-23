package com.shardbound.web;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The API contract names each schema after its Java type's simple name, so two
 * view or request records with one simple name would silently share a schema
 * (one overwrites the other in api/openapi.json). Every name must be unique.
 */
class SchemaNamesTest {

    @Test
    void everyViewAndRequestRecordHasItsOwnSimpleName() {
        List<JavaClass> records = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.shardbound.view", "com.shardbound.web")
                .stream().filter(JavaClass::isRecord).toList();
        Map<String, List<String>> twice = records.stream()
                .collect(Collectors.groupingBy(JavaClass::getSimpleName,
                        Collectors.mapping(JavaClass::getFullName, Collectors.toList())))
                .entrySet().stream().filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(Map.of(), twice);
    }
}
