package com.shardbound.persistence;

import com.shardbound.config.Database;
import com.shardbound.content.RawContent;
import com.shardbound.content.RawContent.AchievementDef;
import com.shardbound.content.RawContent.ChoiceDef;
import com.shardbound.content.RawContent.ConsumableDef;
import com.shardbound.content.RawContent.EncounterDef;
import com.shardbound.content.RawContent.EncounterEnemyDef;
import com.shardbound.content.RawContent.EndingDef;
import com.shardbound.content.RawContent.EnemyDef;
import com.shardbound.content.RawContent.FactionDef;
import com.shardbound.content.RawContent.ItemDef;
import com.shardbound.content.RawContent.LocationDef;
import com.shardbound.content.RawContent.NodeDef;
import com.shardbound.content.RawContent.NpcDef;
import com.shardbound.content.RawContent.QuestDef;
import com.shardbound.content.RawContent.RuleDef;
import com.shardbound.content.RawContent.WeaponDef;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads all static content from PostgreSQL ONCE at startup (spec §2) into a
 * {@link RawContent}. Each table is read with one query on one connection;
 * child rows (conditions, effects, prerequisites) are grouped in Java with a
 * HashMap keyed by parent id, so the whole load is O(total rows).
 */
public class ContentRepository {

    private final Database db;

    public ContentRepository(Database db) {
        this.db = db;
    }

    /** Loads every content table. Time O(total rows), space O(total rows). */
    public RawContent loadAll() {
        try (Connection c = db.getConnection()) {
            return new RawContent(loadFactions(c), loadLocations(c), loadNpcs(c), loadItems(c), loadEnemies(c),
                    loadEncounters(c), loadQuests(c), loadAchievements(c), loadEndings(c), loadNodes(c),
                    loadChoices(c));
        } catch (SQLException e) {
            throw new RepositoryException("Could not load content (did you run ContentImporter?)", e);
        }
    }

    // ---------------------------------------------------------------------

    public List<FactionDef> loadFactions(Connection c) throws SQLException {
        List<FactionDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, "SELECT faction_id, name FROM faction ORDER BY faction_id")) {
            while (rs.next()) {
                result.add(new FactionDef(rs.getString(1), rs.getString(2)));
            }
        }
        return result;
    }

    public List<LocationDef> loadLocations(Connection c) throws SQLException {
        List<LocationDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, "SELECT location_id, name, region FROM location ORDER BY location_id")) {
            while (rs.next()) {
                result.add(new LocationDef(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        return result;
    }

    public List<NpcDef> loadNpcs(Connection c) throws SQLException {
        List<NpcDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, "SELECT npc_id, name, faction_id, location_id FROM npc ORDER BY npc_id")) {
            while (rs.next()) {
                result.add(new NpcDef(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return result;
    }

    /** One LEFT JOIN over the ISA tables: weapon/consumable columns are NULL for other types. */
    public List<ItemDef> loadItems(Connection c) throws SQLException {
        String sql = """
                SELECT i.item_id, i.name, i.type::text, i.description, i.price,
                       w.tier::text, w.attack, w.crit_pct, w.ability,
                       cn.heal, cn.energy
                FROM item i
                LEFT JOIN weapon w      ON w.item_id = i.item_id
                LEFT JOIN consumable cn ON cn.item_id = i.item_id
                ORDER BY i.item_id
                """;
        List<ItemDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, sql)) {
            while (rs.next()) {
                String type = rs.getString(3);
                WeaponDef weapon = "WEAPON".equals(type)
                        ? new WeaponDef(rs.getString(6), rs.getInt(7), rs.getInt(8), rs.getString(9)) : null;
                ConsumableDef consumable = "CONSUMABLE".equals(type)
                        ? new ConsumableDef(rs.getInt(10), rs.getInt(11)) : null;
                result.add(new ItemDef(rs.getString(1), rs.getString(2), type, rs.getString(4), rs.getInt(5),
                        weapon, consumable));
            }
        }
        return result;
    }

    public List<EnemyDef> loadEnemies(Connection c) throws SQLException {
        String sql = "SELECT enemy_id, name, hp, atk, def, spd, xp_reward, gold_reward, ai FROM enemy ORDER BY enemy_id";
        List<EnemyDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, sql)) {
            while (rs.next()) {
                result.add(new EnemyDef(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getString(9)));
            }
        }
        return result;
    }

    public List<EncounterDef> loadEncounters(Connection c) throws SQLException {
        Map<String, List<EncounterEnemyDef>> slots = new HashMap<>();
        try (ResultSet rs = query(c, "SELECT encounter_id, enemy_id, qty FROM encounter_enemy ORDER BY encounter_id, enemy_id")) {
            while (rs.next()) {
                slots.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                     .add(new EncounterEnemyDef(rs.getString(2), rs.getInt(3)));
            }
        }
        List<EncounterDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, "SELECT encounter_id, name, can_flee FROM encounter ORDER BY encounter_id")) {
            while (rs.next()) {
                String id = rs.getString(1);
                result.add(new EncounterDef(id, rs.getString(2), rs.getBoolean(3), slots.getOrDefault(id, List.of())));
            }
        }
        return result;
    }

    public List<QuestDef> loadQuests(Connection c) throws SQLException {
        Map<String, List<String>> prereqs = new HashMap<>();
        try (ResultSet rs = query(c, "SELECT quest_id, requires_id FROM quest_prereq ORDER BY quest_id, requires_id")) {
            while (rs.next()) {
                prereqs.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
            }
        }
        String sql = """
                SELECT quest_id, title, description, giver_npc, reward_item, reward_xp, reward_gold, is_main
                FROM quest ORDER BY quest_id
                """;
        List<QuestDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, sql)) {
            while (rs.next()) {
                String id = rs.getString(1);
                result.add(new QuestDef(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getInt(6), rs.getInt(7), rs.getBoolean(8), prereqs.getOrDefault(id, List.of())));
            }
        }
        return result;
    }

    public List<AchievementDef> loadAchievements(Connection c) throws SQLException {
        List<AchievementDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, "SELECT achievement_id, title, description, hidden FROM achievement ORDER BY achievement_id")) {
            while (rs.next()) {
                result.add(new AchievementDef(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
            }
        }
        return result;
    }

    public List<EndingDef> loadEndings(Connection c) throws SQLException {
        Map<String, List<RuleDef>> conditions = new HashMap<>();
        String condSql = "SELECT ending_id, cond_type, key, op, value, grp FROM ending_condition ORDER BY ending_id, seq";
        try (ResultSet rs = query(c, condSql)) {
            while (rs.next()) {
                conditions.computeIfAbsent(rs.getString(1), k -> new ArrayList<>())
                          .add(new RuleDef(rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6)));
            }
        }
        List<EndingDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, "SELECT ending_id, node_id, title, priority, via_resolver FROM ending ORDER BY priority")) {
            while (rs.next()) {
                String id = rs.getString(1);
                result.add(new EndingDef(id, rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5),
                        conditions.getOrDefault(id, List.of())));
            }
        }
        return result;
    }

    public List<NodeDef> loadNodes(Connection c) throws SQLException {
        String sql = """
                SELECT node_id, chapter, location_id, type::text, title, body,
                       encounter_id, win_node, lose_node, flee_node
                FROM story_node ORDER BY chapter, node_id
                """;
        List<NodeDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, sql)) {
            while (rs.next()) {
                result.add(new NodeDef(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10)));
            }
        }
        return result;
    }

    /** Choices with their conditions and effects (three queries, grouped by choice_id). */
    public List<ChoiceDef> loadChoices(Connection c) throws SQLException {
        Map<Integer, List<RuleDef>> conditions = loadRules(c,
                "SELECT choice_id, cond_type, key, op, value, grp FROM choice_condition ORDER BY choice_id, seq", true);
        Map<Integer, List<RuleDef>> effects = loadRules(c,
                "SELECT choice_id, effect_type, key, NULL, value, 0 FROM choice_effect ORDER BY choice_id, seq", false);
        String sql = """
                SELECT choice_id, from_node, to_node, label, sort_order, hidden_when_locked
                FROM choice ORDER BY from_node, sort_order
                """;
        List<ChoiceDef> result = new ArrayList<>();
        try (ResultSet rs = query(c, sql)) {
            while (rs.next()) {
                int id = rs.getInt(1);
                result.add(new ChoiceDef(id, rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        rs.getBoolean(6), conditions.getOrDefault(id, List.of()), effects.getOrDefault(id, List.of())));
            }
        }
        return result;
    }

    private Map<Integer, List<RuleDef>> loadRules(Connection c, String sql, boolean isCondition) throws SQLException {
        Map<Integer, List<RuleDef>> result = new LinkedHashMap<>();
        try (ResultSet rs = query(c, sql)) {
            while (rs.next()) {
                String op = isCondition ? rs.getString(4) : null;
                result.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>())
                      .add(new RuleDef(rs.getString(2), rs.getString(3), op, rs.getInt(5), rs.getInt(6)));
            }
        }
        return result;
    }

    /**
     * Runs a parameterless SELECT. The statement is closed when the result set
     * is closed (closeOnCompletion), so callers only close the ResultSet.
     */
    private static ResultSet query(Connection c, String sql) throws SQLException {
        SqlLog.record(sql);
        PreparedStatement ps = c.prepareStatement(sql);
        ps.closeOnCompletion();
        return ps.executeQuery();
    }
}
