package com.shardbound.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D28: max hp is not stored; the save_slot CHECK computes the limit from
 * level. This test reads the CHECK expression "40 + 5 * (level - 1)" out of
 * database/01_schema.sql and asserts it gives the same number as
 * Stats.maxHp() for levels 1..50.
 */
class MaxHpFormulaTest {

    @Test
    void javaAndSqlMaxHpFormulasMatch() throws IOException {
        String schema = Files.readString(Path.of("../database/01_schema.sql"));
        Pattern check = Pattern.compile("CHECK\\s*\\(\\s*hp BETWEEN 0 AND\\s+(\\d+)\\s*\\+\\s*(\\d+)\\s*\\*\\s*\\(\\s*level\\s*-\\s*1\\s*\\)\\s*\\)");
        Matcher m = check.matcher(schema);
        assertTrue(m.find(), "save_slot CHECK (hp BETWEEN 0 AND <base> + <perLevel> * (level - 1)) not found");
        int base = Integer.parseInt(m.group(1));
        int perLevel = Integer.parseInt(m.group(2));
        for (int level = 1; level <= 50; level++) {
            int sql = base + perLevel * (level - 1);
            Stats stats = new Stats(1, 0, level, 0, 0);
            assertEquals(sql, stats.maxHp(), "level " + level);
            assertEquals(sql, Stats.maxHpForLevel(level), "level " + level);
        }
    }
}
