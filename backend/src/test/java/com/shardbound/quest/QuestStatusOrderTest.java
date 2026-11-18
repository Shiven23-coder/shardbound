package com.shardbound.quest;

import com.shardbound.model.Tier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QUEST conditions store a QuestStatus ORDINAL in the database (D13), and
 * weapon tiers are compared by enum order in both Java and SQL. These tests
 * read database/01_schema.sql and check the Java enums declare their values in
 * exactly the same order as the PostgreSQL enums, so reordering one side
 * without the other fails the build.
 */
class QuestStatusOrderTest {

    static String schema() throws IOException {
        return Files.readString(Path.of("../database/01_schema.sql"));
    }

    /** Values of "CREATE TYPE name AS ENUM ('A','B',...)" in declaration order. */
    static List<String> sqlEnum(String schema, String typeName) {
        Pattern type = Pattern.compile("CREATE TYPE\\s+" + typeName + "\\s+AS ENUM\\s*\\(([^)]*)\\)");
        Matcher m = type.matcher(schema);
        if (!m.find()) {
            throw new AssertionError("enum " + typeName + " not found in 01_schema.sql");
        }
        List<String> values = new ArrayList<>();
        Matcher literal = Pattern.compile("'([^']*)'").matcher(m.group(1));
        while (literal.find()) {
            values.add(literal.group(1));
        }
        return values;
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> result = new ArrayList<>();
        for (Enum<?> v : values) {
            result.add(v.name());
        }
        return result;
    }

    @Test
    void questStatusMatchesSqlEnumOrder() throws IOException {
        assertEquals(sqlEnum(schema(), "quest_status"), names(QuestStatus.values()));
    }

    @Test
    void weaponTierMatchesSqlEnumOrder() throws IOException {
        assertEquals(sqlEnum(schema(), "weapon_tier"), names(Tier.values()));
    }
}
