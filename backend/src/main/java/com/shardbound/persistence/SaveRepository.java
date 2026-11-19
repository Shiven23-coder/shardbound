package com.shardbound.persistence;

import com.shardbound.engine.port.SaveStore;
import com.shardbound.save.SaveCodec;
import com.shardbound.save.SaveSnapshot;
import com.shardbound.save.SlotSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Save slots as one JSONB document each (P-05). The summary columns are
 * written by the same statement as the document, so they always agree with it.
 */
@Repository
public class SaveRepository implements SaveStore {

    private static final String UPSERT = """
            INSERT INTO save_slot (player_id, slot_no, save_version, level, area_id, playtime_seconds, data)
            VALUES (:player, :slot, :version, :level, :area, :playtime, CAST(:data AS jsonb))
            ON CONFLICT (player_id, slot_no) DO UPDATE
               SET save_version     = EXCLUDED.save_version,
                   level            = EXCLUDED.level,
                   area_id          = EXCLUDED.area_id,
                   playtime_seconds = EXCLUDED.playtime_seconds,
                   data             = EXCLUDED.data,
                   updated_at       = now()
            """;

    private static final String LOAD = """
            SELECT save_version, data::text AS data
            FROM save_slot WHERE player_id = :player AND slot_no = :slot
            FOR UPDATE
            """;

    private static final String WRITE_BACK = """
            UPDATE save_slot SET save_version = :version, data = CAST(:data AS jsonb)
            WHERE player_id = :player AND slot_no = :slot
            """;

    private static final String LIST = """
            SELECT slot_no, area_id, level, playtime_seconds, updated_at
            FROM save_slot WHERE player_id = :player ORDER BY slot_no
            """;

    private static final String DELETE = "DELETE FROM save_slot WHERE player_id = :player AND slot_no = :slot";

    private record StoredSave(int version, String data) { }

    private final JdbcClient jdbc;
    private final SaveCodec codec;

    public SaveRepository(JdbcClient jdbc, SaveCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    @Override
    public void save(int playerId, int slot, SaveSnapshot snapshot, String areaId) {
        jdbc.sql(UPSERT)
                .param("player", playerId)
                .param("slot", slot)
                .param("version", codec.currentVersion())
                .param("level", snapshot.level())
                .param("area", areaId)
                .param("playtime", snapshot.playSeconds())
                .param("data", codec.writeSave(snapshot))
                .update();
    }

    /**
     * Reads a slot and upgrades it to the current format. An upgraded document
     * is written back in the same transaction, so each save is upgraded once.
     */
    @Override
    @Transactional
    public Optional<SaveSnapshot> load(int playerId, int slot) {
        Optional<StoredSave> stored = jdbc.sql(LOAD).param("player", playerId).param("slot", slot)
                .query((rs, row) -> new StoredSave(rs.getInt("save_version"), rs.getString("data")))
                .optional();
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        SaveSnapshot snapshot = codec.readSave(stored.get().data(), stored.get().version());
        if (stored.get().version() < codec.currentVersion()) {
            jdbc.sql(WRITE_BACK)
                    .param("version", codec.currentVersion())
                    .param("data", codec.writeSave(snapshot))
                    .param("player", playerId)
                    .param("slot", slot)
                    .update();
        }
        return Optional.of(snapshot);
    }

    @Override
    public List<SlotSummary> list(int playerId) {
        return jdbc.sql(LIST).param("player", playerId)
                .query((rs, row) -> new SlotSummary(rs.getInt("slot_no"), rs.getString("area_id"),
                        rs.getInt("level"), rs.getInt("playtime_seconds"),
                        rs.getObject("updated_at", Timestamp.class).toInstant()))
                .list();
    }

    @Override
    public boolean delete(int playerId, int slot) {
        return jdbc.sql(DELETE).param("player", playerId).param("slot", slot).update() == 1;
    }
}
