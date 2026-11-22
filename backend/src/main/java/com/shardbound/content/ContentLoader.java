package com.shardbound.content;

import com.shardbound.battle.BattleContent;
import com.shardbound.progression.ProgressionRules;
import com.shardbound.dialogue.DialogueValidator;
import com.shardbound.exception.ContentValidationException;
import com.shardbound.world.AreaMap;
import com.shardbound.world.MapObject;
import com.shardbound.world.MapValidator;
import com.shardbound.world.ObjectType;
import com.shardbound.world.World;
import com.shardbound.world.WorldRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the content files into the immutable {@link GameContent} the engine
 * plays (P-04). Runs once at startup and stops it on any problem:
 * <ol>
 *   <li>JSON Schema validation of every file (shape, types, enums);</li>
 *   <li>reading world.json and the dialogues into {@link RawContent}, and
 *       regions.json, balance.json and the maps into the {@link World};</li>
 *   <li>dialogue checks (reachability, dead ends, references, flags, quests);</li>
 *   <li>map checks (structure, references, BFS reachability of every object);</li>
 *   <li>assembling the objects and ordering the quests (cycles are rejected).</li>
 * </ol>
 */
public class ContentLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ContentLoader.class);

    private final ContentSchemaValidator schemaValidator = new ContentSchemaValidator();

    public GameContent load(ContentFiles files) {
        List<String> problems = schemaValidator.validate(files);
        if (!problems.isEmpty()) {
            throw new ContentValidationException("Content does not match its JSON Schema", problems);
        }
        RawContent raw = new JsonContentReader(files).read();
        WorldReader worldReader = new WorldReader(files);
        World world = worldReader.readWorld();
        WorldRules rules = worldReader.readRules();
        BattleContentReader battleReader = new BattleContentReader(files);
        BattleContent battle = battleReader.readBattle();
        ProgressionRules progression = battleReader.readProgression();

        DialogueValidator dialogues = new DialogueValidator(raw, flagsReadBy(world), world.start().flags().keySet(),
                gateFlagsOf(world));
        if (!dialogues.validate()) {
            throw new ContentValidationException("Dialogue content failed validation", dialogues.errors());
        }
        for (String warning : dialogues.warnings()) {
            LOG.warn("Content: {}", warning);
        }
        Set<String> dialogueIds = raw.dialogues().stream().map(RawContent.DialogueDef::id).collect(Collectors.toSet());
        Set<String> npcIds = raw.npcs().stream().map(RawContent.NpcDef::id).collect(Collectors.toSet());
        List<String> mapErrors = new MapValidator(world, dialogueIds, npcIds, battle.creeps().keySet()).validate();
        if (!mapErrors.isEmpty()) {
            throw new ContentValidationException("Maps failed validation", mapErrors);
        }

        GameContent content = new ContentAssembler().assemble(raw, world, rules, battle, progression);
        content.prepareForPlay();
        LOG.info("Content loaded: {} areas, {} dialogues, {} nodes, {} items, {} quests", world.areas().size(),
                dialogueIds.size(), content.graph().nodes().size(), content.items().size(),
                content.questManager().quests().size());
        return content;
    }

    /** Flags the world reads: map objects' showIf/hideIf, gates and light states. */
    private static Set<String> flagsReadBy(World world) {
        Set<String> flags = new HashSet<>();
        for (AreaMap map : world.maps().values()) {
            for (MapObject o : map.objects()) {
                for (String key : List.of("showIf", "hideIf")) {
                    if (o.property(key) != null) {
                        flags.add(o.property(key));
                    }
                }
            }
        }
        for (World.Edge edge : world.edges()) {
            if (edge.gateFlag() != null) {
                flags.add(edge.gateFlag());
            }
        }
        for (World.Region region : world.regions().values()) {
            for (World.LightState state : region.lightStates()) {
                if (state.flag() != null) {
                    flags.add(state.flag());
                }
            }
        }
        return flags;
    }

    /** Flags that open or close a way: obstacles' showIf/hideIf and edge gates (P-45). */
    private static Set<String> gateFlagsOf(World world) {
        Set<String> flags = new HashSet<>();
        for (AreaMap map : world.maps().values()) {
            for (MapObject o : map.objectsOf(ObjectType.OBSTACLE)) {
                for (String key : List.of("showIf", "hideIf")) {
                    if (o.property(key) != null) {
                        flags.add(o.property(key));
                    }
                }
            }
        }
        for (World.Edge edge : world.edges()) {
            if (edge.gateFlag() != null) {
                flags.add(edge.gateFlag());
            }
        }
        return flags;
    }

    /** Convenience for tests and tools: load straight from a content folder. */
    public static GameContent fromDirectory(Path dir) {
        return new ContentLoader().load(ContentFiles.fromDirectory(dir));
    }
}
