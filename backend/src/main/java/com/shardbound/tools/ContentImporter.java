package com.shardbound.tools;

import com.shardbound.config.AppConfig;
import com.shardbound.config.Database;
import com.shardbound.content.ContentAssembler;
import com.shardbound.content.JsonContentReader;
import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.persistence.SqlLog;
import com.shardbound.story.StoryValidator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Imports content/*.json into PostgreSQL (spec §8.3, D1).
 *
 * <ol>
 *   <li>Read the JSON and run StoryValidator; any error aborts before the DB is touched.</li>
 *   <li>Build the in-memory objects once (ContentAssembler) to catch bad ids early.</li>
 *   <li>In ONE transaction: upsert every row on its natural key, replace the
 *       child rows each parent owns, then delete content that left the JSON —
 *       unless a save still uses it, in which case everything rolls back with
 *       a message naming the row and the save.</li>
 * </ol>
 * Run: {@code .\mvnw.cmd exec:java -Dexec.mainClass=com.shardbound.tools.ContentImporter}
 * <br>Validate only, no database: add {@code -Dexec.args="--dry-run"}.
 */
public class ContentImporter {

    private final Database db;
    private final List<String> summary = new ArrayList<>();

    public ContentImporter(Database db) {
        this.db = db;
    }

    public static void main(String[] args) {
        boolean dryRun = args.length > 0 && args[0].equals("--dry-run");
        AppConfig config = dryRun ? null : AppConfig.load();
        java.io.File contentDir = dryRun ? new java.io.File("../content") : config.contentDir();
        RawContent raw = new JsonContentReader(contentDir).read();
        StoryValidator validator = new StoryValidator(raw);
        boolean ok = validator.validate();
        System.out.println("Validator report:\n" + validator.report());
        if (!ok) {
            System.err.println("Import refused: fix the errors above first.");
            System.exit(1);
        }
        new ContentAssembler().assemble(raw);        // throws with a clear message on bad ids
        if (dryRun) {
            System.out.println("Dry run: content is valid; nothing was written.");
            return;
        }
        try {
            List<String> lines = new ContentImporter(Database.from(config)).importAll(raw);
            System.out.println("Import committed:");
            lines.forEach(line -> System.out.println("  " + line));
        } catch (ImportAbortedException e) {
            System.err.println("Import aborted and rolled back: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * The whole import as one transaction. Time O(total rows) with a constant
     * number of statements per row; space O(total content).
     */
    public List<String> importAll(RawContent raw) {
        summary.clear();
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                Set<String> nodesBefore = ids(c, "SELECT node_id FROM story_node");
                Set<String> endingsBefore = ids(c, "SELECT ending_id FROM ending");
                upsertAll(c, raw);
                deleteRemoved(c, raw);
                c.commit();
                countChanges("nodes", nodeIds(raw), nodesBefore);
                countChanges("endings", endingIds(raw), endingsBefore);
                summary.add(raw.choices().size() + " choices");
                return new ArrayList<>(summary);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof ImportAbortedException abort) {
                    throw abort;
                }
                if (e instanceof SQLException sql && "23503".equals(sql.getSQLState())) {
                    throw new ImportAbortedException("a foreign key blocked the import: " + sql.getMessage());
                }
                throw new ImportAbortedException(e.getMessage());
            }
        } catch (SQLException e) {
            throw new ImportAbortedException("database unavailable: " + e.getMessage());
        }
    }

    // =====================================================================
    // 1. upserts (parents before children; deferred FKs allow forward node refs)
    // =====================================================================

    private void upsertAll(Connection c, RawContent raw) throws SQLException {
        upsertFactions(c, raw);
        upsertLocations(c, raw);
        upsertNpcs(c, raw);
        upsertItems(c, raw);
        upsertEnemies(c, raw);
        upsertEncounters(c, raw);
        upsertNodes(c, raw);
        upsertChoices(c, raw);
        upsertQuests(c, raw);
        upsertAchievements(c, raw);
        upsertEndings(c, raw);
    }

    private void upsertFactions(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO faction (faction_id, name) VALUES (?, ?)
                ON CONFLICT (faction_id) DO UPDATE SET name = EXCLUDED.name
                """;
        try (PreparedStatement ps = prepare(c, sql)) {
            for (var f : raw.factions()) {
                ps.setString(1, f.id());
                ps.setString(2, f.name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertLocations(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO location (location_id, name, region) VALUES (?, ?, ?)
                ON CONFLICT (location_id) DO UPDATE SET name = EXCLUDED.name, region = EXCLUDED.region
                """;
        try (PreparedStatement ps = prepare(c, sql)) {
            for (var l : raw.locations()) {
                ps.setString(1, l.id());
                ps.setString(2, l.name());
                ps.setString(3, l.region());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertNpcs(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO npc (npc_id, name, faction_id, location_id) VALUES (?, ?, ?, ?)
                ON CONFLICT (npc_id) DO UPDATE
                   SET name = EXCLUDED.name, faction_id = EXCLUDED.faction_id, location_id = EXCLUDED.location_id
                """;
        try (PreparedStatement ps = prepare(c, sql)) {
            for (var n : raw.npcs()) {
                ps.setString(1, n.id());
                ps.setString(2, n.name());
                ps.setString(3, n.factionId());
                ps.setString(4, n.locationId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** item + its ISA subtype row; a subtype row of the wrong kind is removed. */
    private void upsertItems(Connection c, RawContent raw) throws SQLException {
        String itemSql = """
                INSERT INTO item (item_id, name, type, description, price) VALUES (?, ?, ?::item_type, ?, ?)
                ON CONFLICT (item_id) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type,
                   description = EXCLUDED.description, price = EXCLUDED.price
                """;
        String weaponSql = """
                INSERT INTO weapon (item_id, tier, attack, crit_pct, ability) VALUES (?, ?::weapon_tier, ?, ?, ?)
                ON CONFLICT (item_id) DO UPDATE SET tier = EXCLUDED.tier, attack = EXCLUDED.attack,
                   crit_pct = EXCLUDED.crit_pct, ability = EXCLUDED.ability
                """;
        String consumableSql = """
                INSERT INTO consumable (item_id, heal, energy) VALUES (?, ?, ?)
                ON CONFLICT (item_id) DO UPDATE SET heal = EXCLUDED.heal, energy = EXCLUDED.energy
                """;
        try (PreparedStatement items = prepare(c, itemSql);
             PreparedStatement weapons = prepare(c, weaponSql);
             PreparedStatement consumables = prepare(c, consumableSql);
             PreparedStatement dropWeapon = prepare(c, "DELETE FROM weapon WHERE item_id = ?");
             PreparedStatement dropConsumable = prepare(c, "DELETE FROM consumable WHERE item_id = ?")) {
            for (var i : raw.items()) {
                items.setString(1, i.id());
                items.setString(2, i.name());
                items.setString(3, i.type());
                items.setString(4, i.description());
                items.setInt(5, i.price());
                items.addBatch();
            }
            items.executeBatch();                       // parents first
            for (var i : raw.items()) {
                if (i.weapon() != null) {
                    weapons.setString(1, i.id());
                    weapons.setString(2, i.weapon().tier());
                    weapons.setInt(3, i.weapon().attack());
                    weapons.setInt(4, i.weapon().critPct());
                    weapons.setString(5, i.weapon().ability());
                    weapons.addBatch();
                } else {
                    dropWeapon.setString(1, i.id());
                    dropWeapon.addBatch();
                }
                if (i.consumable() != null) {
                    consumables.setString(1, i.id());
                    consumables.setInt(2, i.consumable().heal());
                    consumables.setInt(3, i.consumable().energy());
                    consumables.addBatch();
                } else {
                    dropConsumable.setString(1, i.id());
                    dropConsumable.addBatch();
                }
            }
            weapons.executeBatch();
            consumables.executeBatch();
            dropWeapon.executeBatch();
            dropConsumable.executeBatch();
        }
    }

    private void upsertEnemies(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO enemy (enemy_id, name, hp, atk, def, spd, xp_reward, gold_reward, ai)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (enemy_id) DO UPDATE SET name = EXCLUDED.name, hp = EXCLUDED.hp, atk = EXCLUDED.atk,
                   def = EXCLUDED.def, spd = EXCLUDED.spd, xp_reward = EXCLUDED.xp_reward,
                   gold_reward = EXCLUDED.gold_reward, ai = EXCLUDED.ai
                """;
        try (PreparedStatement ps = prepare(c, sql)) {
            for (var e : raw.enemies()) {
                ps.setString(1, e.id());
                ps.setString(2, e.name());
                ps.setInt(3, e.hp());
                ps.setInt(4, e.atk());
                ps.setInt(5, e.def());
                ps.setInt(6, e.spd());
                ps.setInt(7, e.xpReward());
                ps.setInt(8, e.goldReward());
                ps.setString(9, e.ai());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** encounter rows, then their encounter_enemy rows are replaced. */
    private void upsertEncounters(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO encounter (encounter_id, name, can_flee) VALUES (?, ?, ?)
                ON CONFLICT (encounter_id) DO UPDATE SET name = EXCLUDED.name, can_flee = EXCLUDED.can_flee
                """;
        try (PreparedStatement ps = prepare(c, sql);
             PreparedStatement clear = prepare(c, "DELETE FROM encounter_enemy WHERE encounter_id = ?");
             PreparedStatement slot = prepare(c,
                     "INSERT INTO encounter_enemy (encounter_id, enemy_id, qty) VALUES (?, ?, ?)")) {
            for (var e : raw.encounters()) {
                ps.setString(1, e.id());
                ps.setString(2, e.name());
                ps.setBoolean(3, e.canFlee());
                ps.addBatch();
                clear.setString(1, e.id());
                clear.addBatch();
                for (var s : e.enemies()) {
                    slot.setString(1, e.id());
                    slot.setString(2, s.enemyId());
                    slot.setInt(3, s.qty());
                    slot.addBatch();
                }
            }
            ps.executeBatch();
            clear.executeBatch();
            slot.executeBatch();
        }
    }

    private void upsertNodes(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO story_node (node_id, chapter, location_id, type, title, body,
                                        encounter_id, win_node, lose_node, flee_node)
                VALUES (?, ?, ?, ?::node_type, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (node_id) DO UPDATE SET chapter = EXCLUDED.chapter, location_id = EXCLUDED.location_id,
                   type = EXCLUDED.type, title = EXCLUDED.title, body = EXCLUDED.body,
                   encounter_id = EXCLUDED.encounter_id, win_node = EXCLUDED.win_node,
                   lose_node = EXCLUDED.lose_node, flee_node = EXCLUDED.flee_node
                """;
        try (PreparedStatement ps = prepare(c, sql)) {
            for (var n : raw.nodes()) {
                ps.setString(1, n.id());
                ps.setInt(2, n.chapter());
                ps.setString(3, n.locationId());
                ps.setString(4, n.type());
                ps.setString(5, n.title());
                ps.setString(6, n.body());
                ps.setString(7, n.encounterId());
                ps.setString(8, n.winNode());
                ps.setString(9, n.loseNode());
                ps.setString(10, n.fleeNode());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Choices upsert on (from_node, sort_order) and RETURN their choice_id, so
     * an unchanged choice keeps its id and saves pointing at it stay valid (D1).
     * Conditions and effects are owned rows: replaced every time.
     */
    private void upsertChoices(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO choice (from_node, to_node, label, sort_order, hidden_when_locked)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (from_node, sort_order) DO UPDATE SET to_node = EXCLUDED.to_node,
                   label = EXCLUDED.label, hidden_when_locked = EXCLUDED.hidden_when_locked
                RETURNING choice_id
                """;
        String clearConditions = "DELETE FROM choice_condition WHERE choice_id = ?";
        String clearEffects = "DELETE FROM choice_effect WHERE choice_id = ?";
        String condSql = """
                INSERT INTO choice_condition (choice_id, seq, grp, cond_type, key, op, value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        String effectSql = "INSERT INTO choice_effect (choice_id, seq, effect_type, key, value) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = prepare(c, sql);
             PreparedStatement clearC = prepare(c, clearConditions);
             PreparedStatement clearE = prepare(c, clearEffects);
             PreparedStatement cond = prepare(c, condSql);
             PreparedStatement eff = prepare(c, effectSql)) {
            for (ChoiceDef choice : raw.choices()) {
                int choiceId = upsertOneChoice(ps, choice);
                clearC.setInt(1, choiceId);
                clearC.addBatch();
                clearE.setInt(1, choiceId);
                clearE.addBatch();
                addConditionRows(cond, choiceId, choice.conditions());
                addEffectRows(eff, choiceId, choice.effects());
            }
            clearC.executeBatch();
            clearE.executeBatch();
            cond.executeBatch();
            eff.executeBatch();
        }
    }

    private int upsertOneChoice(PreparedStatement ps, ChoiceDef choice) throws SQLException {
        ps.setString(1, choice.fromNode());
        ps.setString(2, choice.toNode());
        ps.setString(3, choice.label());
        ps.setInt(4, choice.sortOrder());
        ps.setBoolean(5, choice.hiddenWhenLocked());
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void addConditionRows(PreparedStatement ps, Object ownerId, List<RuleDef> rows) throws SQLException {
        int seq = 0;
        for (RuleDef r : rows) {
            ps.setObject(1, ownerId);
            ps.setInt(2, seq++);
            ps.setInt(3, r.grp());
            ps.setString(4, r.type());
            ps.setString(5, r.key());
            ps.setString(6, r.op());
            ps.setInt(7, r.value());
            ps.addBatch();
        }
    }

    private static void addEffectRows(PreparedStatement ps, int choiceId, List<RuleDef> rows) throws SQLException {
        int seq = 0;
        for (RuleDef r : rows) {
            ps.setInt(1, choiceId);
            ps.setInt(2, seq++);
            ps.setString(3, r.type());
            ps.setString(4, r.key());
            ps.setInt(5, r.value());
            ps.addBatch();
        }
    }

    /** Quests first (all of them), then their prerequisite rows are replaced. */
    private void upsertQuests(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO quest (quest_id, title, description, giver_npc, reward_item, reward_xp, reward_gold, is_main)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (quest_id) DO UPDATE SET title = EXCLUDED.title, description = EXCLUDED.description,
                   giver_npc = EXCLUDED.giver_npc, reward_item = EXCLUDED.reward_item,
                   reward_xp = EXCLUDED.reward_xp, reward_gold = EXCLUDED.reward_gold, is_main = EXCLUDED.is_main
                """;
        try (PreparedStatement ps = prepare(c, sql);
             PreparedStatement clear = prepare(c, "DELETE FROM quest_prereq WHERE quest_id = ?");
             PreparedStatement prereq = prepare(c, "INSERT INTO quest_prereq (quest_id, requires_id) VALUES (?, ?)")) {
            for (var q : raw.quests()) {
                ps.setString(1, q.id());
                ps.setString(2, q.title());
                ps.setString(3, q.description());
                ps.setString(4, q.giverNpc());
                ps.setString(5, q.rewardItem());
                ps.setInt(6, q.rewardXp());
                ps.setInt(7, q.rewardGold());
                ps.setBoolean(8, q.isMain());
                ps.addBatch();
                clear.setString(1, q.id());
                clear.addBatch();
                for (String requires : q.prereqs()) {
                    prereq.setString(1, q.id());
                    prereq.setString(2, requires);
                    prereq.addBatch();
                }
            }
            ps.executeBatch();
            clear.executeBatch();
            prereq.executeBatch();
        }
    }

    private void upsertAchievements(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO achievement (achievement_id, title, description, hidden) VALUES (?, ?, ?, ?)
                ON CONFLICT (achievement_id) DO UPDATE SET title = EXCLUDED.title,
                   description = EXCLUDED.description, hidden = EXCLUDED.hidden
                """;
        try (PreparedStatement ps = prepare(c, sql)) {
            for (var a : raw.achievements()) {
                ps.setString(1, a.id());
                ps.setString(2, a.title());
                ps.setString(3, a.description());
                ps.setBoolean(4, a.hidden());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void upsertEndings(Connection c, RawContent raw) throws SQLException {
        String sql = """
                INSERT INTO ending (ending_id, node_id, title, priority, via_resolver) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (ending_id) DO UPDATE SET node_id = EXCLUDED.node_id, title = EXCLUDED.title,
                   priority = EXCLUDED.priority, via_resolver = EXCLUDED.via_resolver
                """;
        String condSql = """
                INSERT INTO ending_condition (ending_id, seq, grp, cond_type, key, op, value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = prepare(c, sql);
             PreparedStatement clear = prepare(c, "DELETE FROM ending_condition WHERE ending_id = ?");
             PreparedStatement cond = prepare(c, condSql)) {
            for (var e : raw.endings()) {
                ps.setString(1, e.id());
                ps.setString(2, e.nodeId());
                ps.setString(3, e.title());
                ps.setInt(4, e.priority());
                ps.setBoolean(5, e.viaResolver());
                ps.addBatch();
                clear.setString(1, e.id());
                clear.addBatch();
                addConditionRows(cond, e.id(), e.conditions());
            }
            ps.executeBatch();
            clear.executeBatch();
            cond.executeBatch();
        }
    }

    // =====================================================================
    // 2. delete content that is no longer in the JSON (guarded, D1)
    // =====================================================================

    private void deleteRemoved(Connection c, RawContent raw) throws SQLException {
        // Endings and achievements are referenced by player progress, not saves.
        deleteMissing(c, "ending", "ending_id", endingIds(raw),
                "SELECT NULL::int, p.username, NULL::smallint FROM player_ending pe JOIN player p USING (player_id) WHERE pe.ending_id = ?");
        deleteMissing(c, "achievement", "achievement_id", achievementIds(raw),
                "SELECT NULL::int, p.username, NULL::smallint FROM player_achievement pa JOIN player p USING (player_id) WHERE pa.achievement_id = ?");
        deleteRemovedChoices(c, raw);
        deleteMissing(c, "story_node", "node_id", nodeIds(raw), """
                SELECT s.save_id, p.username, s.slot_no FROM save_slot s JOIN player p USING (player_id)
                WHERE s.current_node = ?
                UNION ALL
                SELECT s.save_id, p.username, s.slot_no FROM save_history h
                JOIN save_slot s USING (save_id) JOIN player p USING (player_id) WHERE h.node_id = ?
                """);
        deleteMissing(c, "quest", "quest_id", questIds(raw), """
                SELECT s.save_id, p.username, s.slot_no FROM save_quest q
                JOIN save_slot s USING (save_id) JOIN player p USING (player_id) WHERE q.quest_id = ?
                """);
        deleteMissing(c, "encounter", "encounter_id", idsOf(raw.encounters(), RawContent.EncounterDef::id), null);
        deleteMissing(c, "enemy", "enemy_id", idsOf(raw.enemies(), RawContent.EnemyDef::id), null);
        deleteMissing(c, "item", "item_id", idsOf(raw.items(), RawContent.ItemDef::id), """
                SELECT s.save_id, p.username, s.slot_no FROM save_inventory i
                JOIN save_slot s USING (save_id) JOIN player p USING (player_id) WHERE i.item_id = ?
                """);
        deleteMissing(c, "npc", "npc_id", idsOf(raw.npcs(), RawContent.NpcDef::id), """
                SELECT s.save_id, p.username, s.slot_no FROM save_affinity a
                JOIN save_slot s USING (save_id) JOIN player p USING (player_id) WHERE a.npc_id = ?
                """);
        deleteMissing(c, "location", "location_id", idsOf(raw.locations(), RawContent.LocationDef::id), null);
        deleteMissing(c, "faction", "faction_id", idsOf(raw.factions(), RawContent.FactionDef::id), """
                SELECT s.save_id, p.username, s.slot_no FROM save_reputation r
                JOIN save_slot s USING (save_id) JOIN player p USING (player_id) WHERE r.faction_id = ?
                """);
    }

    /**
     * Deletes rows of {@code table} whose id is not in {@code keep}. Before each
     * delete, {@code blockerSql} (if given) looks for a save/player still using
     * the row; if one exists the import aborts with a message naming it.
     * Time O(existing rows).
     */
    private void deleteMissing(Connection c, String table, String idColumn, Set<String> keep, String blockerSql)
            throws SQLException {
        int deleted = 0;
        for (String id : ids(c, "SELECT " + idColumn + " FROM " + table)) {
            if (keep.contains(id)) {
                continue;
            }
            if (blockerSql != null) {
                String blocker = findBlocker(c, blockerSql, id);
                if (blocker != null) {
                    throw new ImportAbortedException(table + " '" + id + "' was removed from the JSON but "
                            + blocker + " still references it. Delete that save or keep the row.");
                }
            }
            try (PreparedStatement ps = prepare(c, "DELETE FROM " + table + " WHERE " + idColumn + " = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            deleted++;
        }
        if (deleted > 0) {
            summary.add("deleted " + deleted + " " + table + " row(s) no longer in the JSON");
        }
    }

    /** Choices whose (from_node, sort_order) is gone. save_history.choice_id is SET NULL, so never blocked. */
    private void deleteRemovedChoices(Connection c, RawContent raw) throws SQLException {
        Set<String> keep = new HashSet<>();
        for (ChoiceDef choice : raw.choices()) {
            keep.add(choice.fromNode() + "#" + choice.sortOrder());
        }
        List<Integer> toDelete = new ArrayList<>();
        try (PreparedStatement ps = prepare(c, "SELECT choice_id, from_node, sort_order FROM choice");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (!keep.contains(rs.getString(2) + "#" + rs.getInt(3))) {
                    toDelete.add(rs.getInt(1));
                }
            }
        }
        try (PreparedStatement ps = prepare(c, "DELETE FROM choice WHERE choice_id = ?")) {
            for (int id : toDelete) {
                ps.setInt(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        if (!toDelete.isEmpty()) {
            summary.add("deleted " + toDelete.size() + " choice(s) no longer in the JSON");
        }
    }

    /** Returns "save #12 (player 'alice', slot 1)" / "player 'alice'" or null. */
    private String findBlocker(Connection c, String sql, String id) throws SQLException {
        try (PreparedStatement ps = prepare(c, sql)) {
            int params = ps.getParameterMetaData().getParameterCount();
            for (int i = 1; i <= params; i++) {
                ps.setString(i, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int saveId = rs.getInt(1);
                if (rs.wasNull()) {
                    return "player '" + rs.getString(2) + "'";
                }
                return "save #" + saveId + " (player '" + rs.getString(2) + "', slot " + rs.getInt(3) + ")";
            }
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static PreparedStatement prepare(Connection c, String sql) throws SQLException {
        SqlLog.record(sql);
        return c.prepareStatement(sql);
    }

    private static Set<String> ids(Connection c, String sql) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement ps = prepare(c, sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    private void countChanges(String what, Set<String> now, Set<String> before) {
        int inserted = 0;
        for (String id : now) {
            if (!before.contains(id)) inserted++;
        }
        summary.add(what + ": " + now.size() + " total (" + inserted + " new, " + (now.size() - inserted) + " updated)");
    }

    private static Set<String> nodeIds(RawContent raw) {
        Set<String> ids = new HashSet<>();
        for (var n : raw.nodes()) ids.add(n.id());
        return ids;
    }

    private static Set<String> endingIds(RawContent raw) {
        Set<String> ids = new HashSet<>();
        for (var e : raw.endings()) ids.add(e.id());
        return ids;
    }

    private static Set<String> achievementIds(RawContent raw) {
        Set<String> ids = new HashSet<>();
        for (var a : raw.achievements()) ids.add(a.id());
        return ids;
    }

    private static Set<String> questIds(RawContent raw) {
        Set<String> ids = new HashSet<>();
        for (var q : raw.quests()) ids.add(q.id());
        return ids;
    }

    /** Ids of a list of definitions, e.g. idsOf(raw.items(), ItemDef::id). O(n). */
    private static <T> Set<String> idsOf(List<T> rows, Function<T, String> id) {
        Set<String> ids = new HashSet<>();
        for (T row : rows) {
            ids.add(id.apply(row));
        }
        return ids;
    }

    /** Thrown to abort (and roll back) the import with a human-readable reason. */
    public static class ImportAbortedException extends RuntimeException {
        public ImportAbortedException(String message) {
            super(message);
        }
    }
}
