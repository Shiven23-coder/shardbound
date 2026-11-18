package com.shardbound.persistence;

import com.shardbound.config.Database;
import com.shardbound.engine.GameState;
import com.shardbound.engine.Step;
import com.shardbound.exception.SaveFailedException;
import com.shardbound.exception.SaveNotFoundException;
import com.shardbound.item.Inventory;
import com.shardbound.item.Item;
import com.shardbound.item.ItemStack;
import com.shardbound.model.Faction;
import com.shardbound.persistence.SaveData.HistoryRow;
import com.shardbound.persistence.SaveData.InventoryRow;
import com.shardbound.persistence.SaveData.SlotSummary;
import com.shardbound.quest.QuestStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Save slots (spec §4.4, §6.10). Only GameEngine calls this class (D22).
 *
 * <p>{@link #save} — the save TRANSACTION — is a viva algorithm
 * (CLAUDE.md rule 8; docs/STUDY_GUIDE.md), together with its private helpers. Its integration
 * test is {@code SaveRepositoryIT}. Loading, listing and deleting are done.
 */
public class SaveRepository {

    /** Spec §4.4: insert the slot or overwrite it, bumping version, and return save_id. */
    static final String UPSERT_SLOT = """
            INSERT INTO save_slot (player_id, slot_no, current_node, hp, energy, level, xp, gold,
                                   rng_seed, demo_mode, play_seconds)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (player_id, slot_no) DO UPDATE SET
              current_node = EXCLUDED.current_node, hp = EXCLUDED.hp,
              energy = EXCLUDED.energy, level = EXCLUDED.level, xp = EXCLUDED.xp, gold = EXCLUDED.gold,
              rng_seed = EXCLUDED.rng_seed, demo_mode = EXCLUDED.demo_mode,
              play_seconds = EXCLUDED.play_seconds,
              version = save_slot.version + 1
            RETURNING save_id
            """;

    private static final String SELECT_SLOT = """
            SELECT save_id, current_node, hp, energy, level, xp, gold, rng_seed, demo_mode, play_seconds
            FROM save_slot WHERE player_id = ? AND slot_no = ?
            """;
    private static final String SELECT_INVENTORY =
            "SELECT item_id, qty, equipped FROM save_inventory WHERE save_id = ?";
    private static final String SELECT_FLAGS =
            "SELECT flag_key, value FROM save_flag WHERE save_id = ?";
    private static final String SELECT_REPUTATION =
            "SELECT faction_id, value FROM save_reputation WHERE save_id = ?";
    private static final String SELECT_AFFINITY =
            "SELECT npc_id, value FROM save_affinity WHERE save_id = ?";
    private static final String SELECT_QUESTS =
            "SELECT quest_id, status::text FROM save_quest WHERE save_id = ?";
    private static final String SELECT_HISTORY =
            "SELECT step_no, node_id, choice_id FROM save_history WHERE save_id = ? ORDER BY step_no";
    private static final String LIST_SLOTS = """
            SELECT slot_no, current_node, level, play_seconds, to_char(updated_at, 'YYYY-MM-DD HH24:MI')
            FROM save_slot WHERE player_id = ? ORDER BY slot_no
            """;
    // Child rows go too: every save_* child FK is ON DELETE CASCADE.
    private static final String DELETE_SLOT =
            "DELETE FROM save_slot WHERE player_id = ? AND slot_no = ?";

    private final Database db;
    private final boolean simulateCrash;

    public SaveRepository(Database db, boolean simulateCrash) {
        this.db = db;
        this.simulateCrash = simulateCrash;
    }

    // =====================================================================
    // The save transaction (viva algorithm, CLAUDE.md rule 8)
    // =====================================================================

    /**
     * Writes the whole state into slot {@code slot} of player {@code playerId}
     * as ONE transaction: either every row is written, or none are and the
     * previous save in that slot is untouched (atomicity).
     *
     * <p>Follow spec §4.4:
     * <ol>
     *   <li>{@code c.setAutoCommit(false)}</li>
     *   <li>{@code upsertSlot} — {@link #UPSERT_SLOT}, returns save_id</li>
     *   <li>{@code deleteChildren} — remove the old save_inventory, save_flag,
     *       save_reputation, save_affinity, save_quest, save_history rows</li>
     *   <li>{@code insertInventory} (mark the equipped weapon), then — if
     *       {@code simulateCrash} is true — throw an SQLException to prove the
     *       rollback (spec §4.4 crash simulation)</li>
     *   <li>{@code insertFlags}, {@code insertReputation}, {@code insertAffinity},
     *       {@code insertQuests} (cast with {@code ?::quest_status}, D5),
     *       {@code insertHistory} (from {@code s.historyOldestFirst()}, step_no from 1, S7)</li>
     *   <li>{@code c.commit()}; on any SQLException {@code c.rollback()} and throw
     *       {@code SaveFailedException("Save rolled back; previous save intact", e)};
     *       if the connection itself fails, throw {@code SaveFailedException("DB unavailable", e)}</li>
     * </ol>
     * Use {@code addBatch()}/{@code executeBatch()} for the child rows and
     * call {@code SqlLog.record(sql)} for each statement so the Inspector shows it.
     *
     * <p>Read the state only through its public views: {@code s.inventory()},
     * {@code s.flagsView()}, {@code s.reputationView()}, {@code s.affinityView()},
     * {@code s.questsView()}, {@code s.historyOldestFirst()}, {@code s.stats()},
     * {@code s.rngSeed()}, {@code s.demoMode()}, {@code s.playSeconds()},
     * {@code s.currentNode()}.
     *
     * <p>Complexity: time O(n) for n rows, written in a constant number of
     * batches, space O(1) beyond the batches.
     *
     * @throws SaveFailedException if anything goes wrong (the old save survives)
     */
    public void save(GameState s, int playerId, int slot) {
        try (Connection c = db.getConnection()) {
            // Step 1: start a transaction. Nothing below is visible to other connections,
            // and nothing is permanent, until commit().
            c.setAutoCommit(false);
            try {
                // Step 2: insert the slot or overwrite it (version + 1); get its save_id.
                int saveId = upsertSlot(c, s, playerId, slot);
                // Step 3: the old child rows of this slot are replaced, not merged.
                deleteChildren(c, saveId);
                // Step 4: write the child rows, one batch per table.
                insertInventory(c, saveId, s.inventory());
                if (simulateCrash) {
                    // Demo (spec §4.4): fail halfway, AFTER some rows were written,
                    // to prove the rollback keeps the previous save intact.
                    throw new SQLException("Simulated crash after insertInventory");
                }
                insertFlags(c, saveId, s.flagsView());
                insertReputation(c, saveId, s.reputationView());
                insertAffinity(c, saveId, s.affinityView());
                insertQuests(c, saveId, s.questsView());
                insertHistory(c, saveId, s.historyOldestFirst());
                // Step 5: all statements succeeded: make them permanent together.
                c.commit();
            } catch (SQLException | RuntimeException e) {
                // Step 6: anything failed: undo every statement of this save. The previous
                // save in this slot is exactly as it was before step 2.
                c.rollback();
                throw new SaveFailedException("Save rolled back; previous save intact", e);
            }
        } catch (SQLException e) {
            // Opening the connection (or the rollback itself) failed.
            throw new SaveFailedException("DB unavailable", e);
        }
    }

    /** Runs {@link #UPSERT_SLOT}; returns the slot's save_id (new or existing). O(1) round trips. */
    private int upsertSlot(Connection c, GameState s, int playerId, int slot) throws SQLException {
        SqlLog.record(UPSERT_SLOT);
        try (PreparedStatement ps = c.prepareStatement(UPSERT_SLOT)) {
            ps.setInt(1, playerId);
            ps.setInt(2, slot);
            ps.setString(3, s.currentNode());
            ps.setInt(4, s.stats().hp());
            ps.setInt(5, s.stats().energy());
            ps.setInt(6, s.stats().level());
            ps.setInt(7, s.stats().xp());
            ps.setInt(8, s.stats().gold());
            ps.setLong(9, s.rngSeed());
            ps.setBoolean(10, s.demoMode());
            ps.setInt(11, s.playSeconds());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** The six child tables of a save, cleared before rewriting. */
    private static final String[] CHILD_TABLES = {
            "save_inventory", "save_flag", "save_reputation", "save_affinity", "save_quest", "save_history"};

    /** Deletes this save's rows from every child table. Six statements, one batch. O(n) rows. */
    private void deleteChildren(Connection c, int saveId) throws SQLException {
        for (String table : CHILD_TABLES) {
            // Table names come from the constant array above, never from user input.
            String sql = "DELETE FROM " + table + " WHERE save_id = ?";
            SqlLog.record(sql);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, saveId);
                ps.executeUpdate();
            }
        }
    }

    /** Batch-inserts (save_id, item_id, qty, equipped); exactly one row is equipped (if any). O(n). */
    private void insertInventory(Connection c, int saveId, Inventory inventory) throws SQLException {
        String sql = "INSERT INTO save_inventory (save_id, item_id, qty, equipped) VALUES (?, ?, ?, ?)";
        SqlLog.record(sql);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (ItemStack<Item> stack : inventory.stacksView()) {
                String itemId = stack.item().id();
                ps.setInt(1, saveId);
                ps.setString(2, itemId);
                ps.setInt(3, stack.qty());
                ps.setBoolean(4, itemId.equals(inventory.equippedWeaponId()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Batch-inserts (save_id, flag_key, value). O(n). */
    private void insertFlags(Connection c, int saveId, Map<String, Integer> flags) throws SQLException {
        String sql = "INSERT INTO save_flag (save_id, flag_key, value) VALUES (?, ?, ?)";
        SqlLog.record(sql);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> flag : flags.entrySet()) {
                ps.setInt(1, saveId);
                ps.setString(2, flag.getKey());
                ps.setInt(3, flag.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Batch-inserts (save_id, faction_id, value): one row per faction. O(4). */
    private void insertReputation(Connection c, int saveId, Map<Faction, Integer> reputation) throws SQLException {
        String sql = "INSERT INTO save_reputation (save_id, faction_id, value) VALUES (?, ?, ?)";
        SqlLog.record(sql);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<Faction, Integer> rep : reputation.entrySet()) {
                ps.setInt(1, saveId);
                ps.setString(2, rep.getKey().name());
                ps.setInt(3, rep.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Batch-inserts (save_id, npc_id, value). O(n). */
    private void insertAffinity(Connection c, int saveId, Map<String, Integer> affinity) throws SQLException {
        String sql = "INSERT INTO save_affinity (save_id, npc_id, value) VALUES (?, ?, ?)";
        SqlLog.record(sql);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> aff : affinity.entrySet()) {
                ps.setInt(1, saveId);
                ps.setString(2, aff.getKey());
                ps.setInt(3, aff.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Batch-inserts (save_id, quest_id, status); the cast turns the name into the enum (D5). O(n). */
    private void insertQuests(Connection c, int saveId, Map<String, QuestStatus> quests) throws SQLException {
        String sql = "INSERT INTO save_quest (save_id, quest_id, status) VALUES (?, ?, ?::quest_status)";
        SqlLog.record(sql);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, QuestStatus> quest : quests.entrySet()) {
                ps.setInt(1, saveId);
                ps.setString(2, quest.getKey());
                ps.setString(3, quest.getValue().name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Batch-inserts (save_id, step_no, node_id, choice_id) with step_no 1 = oldest (S7).
     * choice_id 0 means "choice no longer exists" and is stored as NULL (D1). O(n).
     */
    private void insertHistory(Connection c, int saveId, List<Step> history) throws SQLException {
        String sql = "INSERT INTO save_history (save_id, step_no, node_id, choice_id) VALUES (?, ?, ?, ?)";
        SqlLog.record(sql);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int stepNo = 1;
            for (Step step : history) {
                ps.setInt(1, saveId);
                ps.setInt(2, stepNo);
                ps.setString(3, step.nodeId());
                if (step.choiceId() == 0) {
                    ps.setNull(4, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(4, step.choiceId());
                }
                ps.addBatch();
                stepNo++;
            }
            ps.executeBatch();
        }
    }

    /** Whether the crash simulation is switched on (AppConfig.simulateSaveCrash). */
    public boolean simulateCrash() {
        return simulateCrash;
    }

    // =====================================================================
    // Load / list / delete
    // =====================================================================

    /**
     * Reads one slot and its child rows (7 small queries on one connection).
     * Time O(n) rows, space O(n).
     *
     * @throws SaveNotFoundException if the slot is empty (HTTP 404, D27)
     */
    public SaveData load(int playerId, int slot) {
        SqlLog.record(SELECT_SLOT);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_SLOT)) {
            ps.setInt(1, playerId);
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SaveNotFoundException(playerId, slot);
                }
                int saveId = rs.getInt("save_id");
                return new SaveData(saveId, playerId, slot, rs.getString("current_node"),
                        rs.getInt("hp"), rs.getInt("energy"), rs.getInt("level"), rs.getInt("xp"),
                        rs.getInt("gold"), rs.getLong("rng_seed"), rs.getBoolean("demo_mode"),
                        rs.getInt("play_seconds"),
                        loadInventory(c, saveId), loadIntMap(c, SELECT_FLAGS, saveId),
                        loadIntMap(c, SELECT_REPUTATION, saveId), loadIntMap(c, SELECT_AFFINITY, saveId),
                        loadQuests(c, saveId), loadHistory(c, saveId));
            }
        } catch (SQLException e) {
            throw new SaveFailedException("Could not load slot " + slot, e);
        }
    }

    /** Existing slots for a player, slot order. One query. */
    public List<SlotSummary> listSlots(int playerId) {
        SqlLog.record(LIST_SLOTS);
        List<SlotSummary> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(LIST_SLOTS)) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SlotSummary(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                            rs.getString(5)));
                }
            }
        } catch (SQLException e) {
            throw new SaveFailedException("Could not list saves", e);
        }
        return result;
    }

    /** Deletes a slot (children cascade). @throws SaveNotFoundException if it was empty. */
    public void delete(int playerId, int slot) {
        SqlLog.record(DELETE_SLOT);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(DELETE_SLOT)) {
            ps.setInt(1, playerId);
            ps.setInt(2, slot);
            if (ps.executeUpdate() == 0) {
                throw new SaveNotFoundException(playerId, slot);
            }
        } catch (SQLException e) {
            throw new SaveFailedException("Could not delete slot " + slot, e);
        }
    }

    // ---- load helpers ----

    private List<InventoryRow> loadInventory(Connection c, int saveId) throws SQLException {
        SqlLog.record(SELECT_INVENTORY);
        List<InventoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_INVENTORY)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new InventoryRow(rs.getString(1), rs.getInt(2), rs.getBoolean(3)));
                }
            }
        }
        return rows;
    }

    /** Reads a two-column (key, int value) child table. */
    private Map<String, Integer> loadIntMap(Connection c, String sql, int saveId) throws SQLException {
        SqlLog.record(sql);
        Map<String, Integer> result = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return result;
    }

    private Map<String, String> loadQuests(Connection c, int saveId) throws SQLException {
        SqlLog.record(SELECT_QUESTS);
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_QUESTS)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return result;
    }

    private List<HistoryRow> loadHistory(Connection c, int saveId) throws SQLException {
        SqlLog.record(SELECT_HISTORY);
        List<HistoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SELECT_HISTORY)) {
            ps.setInt(1, saveId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int choiceId = rs.getInt(3);          // 0 if NULL (removed choice, D1)
                    rows.add(new HistoryRow(rs.getInt(1), rs.getString(2), choiceId));
                }
            }
        }
        return rows;
    }
}
