package com.shardbound.content;

import com.shardbound.exception.ContentValidationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Content files against their JSON Schemas (P-04). Runs in CI with every
 * build, so a malformed content change can never be merged.
 */
class ContentSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ContentFiles REAL = ContentFiles.fromDirectory(Path.of("../content"));

    /** The real files with one file changed by {@code edit}. */
    private static ContentFiles withEdit(String file, Consumer<ObjectNode> edit) {
        ObjectNode root = (ObjectNode) JSON.readTree(REAL.text(file));
        edit.accept(root);
        Map<String, String> texts = new HashMap<>(REAL.all());
        texts.put(file, JSON.writeValueAsString(root));
        return ContentFiles.of(texts);
    }

    private static List<String> problems(ContentFiles files) {
        return new ContentSchemaValidator().validate(files);
    }

    private static boolean anyContains(List<String> problems, String... parts) {
        for (String problem : problems) {
            boolean all = true;
            for (String part : parts) {
                all &= problem.contains(part);
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyRealContentFileMatchesItsSchema() {
        assertEquals(List.of(), problems(REAL));
    }

    @Test
    void theRealContentLoadsAndPassesTheDialogueAndMapChecks() {
        GameContent content = new ContentLoader().load(REAL);
        assertTrue(content.graph().nodes().size() > 10);
    }

    @Test
    void aWrongEnumValueIsReportedWithItsLocation() {
        ContentFiles broken = withEdit("world.json",
                world -> ((ObjectNode) world.get("items").get(0)).put("type", "WEPON"));
        assertTrue(anyContains(problems(broken), "world.json", "/items/0/type"), problems(broken).toString());
    }

    @Test
    void aMissingRequiredFieldIsReported() {
        ContentFiles broken = withEdit("dialogue/maren.json",
                dialogue -> ((ObjectNode) dialogue.get("nodes").get(2)).remove("title"));
        assertTrue(anyContains(problems(broken), "dialogue/maren.json", "/nodes/2", "title"),
                problems(broken).toString());
    }

    @Test
    void aCombatNodeNeedsItsEncounterAndExits() {
        ContentFiles broken = withEdit("dialogue/alley_mouth.json", dialogue -> {
            ArrayNode nodes = (ArrayNode) dialogue.get("nodes");
            for (int i = 0; i < nodes.size(); i++) {
                ObjectNode node = (ObjectNode) nodes.get(i);
                if ("COMBAT".equals(node.path("type").asString())) {
                    node.remove("win");
                }
            }
        });
        assertTrue(anyContains(problems(broken), "dialogue/alley_mouth.json", "win"), problems(broken).toString());
    }

    @Test
    void aChoiceEitherLeadsSomewhereOrEndsTheDialogue() {
        ContentFiles broken = withEdit("dialogue/brann.json",
                dialogue -> ((ObjectNode) dialogue.get("nodes").get(0).get("choices").get(0)).remove("end"));
        assertTrue(anyContains(problems(broken), "dialogue/brann.json", "/nodes/0/choices/0"),
                problems(broken).toString());
    }

    @Test
    void everyFileMustNameItsSchema() {
        ContentFiles broken = withEdit("balance.json", balance -> balance.remove("$schema"));
        assertTrue(anyContains(problems(broken), "balance.json", "\"$schema\""), problems(broken).toString());
    }

    @Test
    void aQuestConditionMustUseAStatusName() {
        ContentFiles broken = withEdit("world.json", world -> {
            ObjectNode condition = (ObjectNode) world.get("endings").get(0).get("conditions").get(0);
            condition.put("value", 3);
        });
        assertTrue(anyContains(problems(broken), "world.json", "/endings/0/conditions/0/value"),
                problems(broken).toString());
    }

    @Test
    void unknownFieldsAreRejectedSoTyposCannotHide() {
        ContentFiles broken = withEdit("world.json",
                world -> ((ObjectNode) world.get("encounters").get(0)).put("attack", 5));
        assertTrue(anyContains(problems(broken), "world.json", "/encounters/0"), problems(broken).toString());
    }

    @Test
    void theLoaderRefusesToStartOnSchemaProblems() {
        ContentFiles broken = withEdit("world.json", world -> world.remove("items"));
        ContentValidationException e = assertThrows(ContentValidationException.class,
                () -> new ContentLoader().load(broken));
        assertTrue(e.getMessage().contains("items"), e.getMessage());
    }

    @Test
    void invalidJsonIsReportedPerFile() {
        Map<String, String> texts = new HashMap<>(REAL.all());
        texts.put("dialogue/finale.json", "{ not json");
        assertTrue(anyContains(problems(ContentFiles.of(texts)), "dialogue/finale.json", "not valid JSON"));
    }
}
