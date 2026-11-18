-- =====================================================================
-- Shardbound — 03_views_indexes.sql
-- Run after 02_functions_triggers.sql.
-- =====================================================================

-- ---------------------------------------------------------------------
-- v_leaderboard: one row per player.
-- Two LEFT JOINs on the same player multiply rows (endings x achievements),
-- so plain COUNT(*) would be wrong; COUNT(DISTINCT ...) counts each once.
-- LEFT JOIN keeps players who have no endings or achievements yet (0 rows).
-- ---------------------------------------------------------------------
CREATE VIEW v_leaderboard AS
SELECT p.username,
       COUNT(DISTINCT pe.ending_id)      AS endings,
       COUNT(DISTINCT pa.achievement_id) AS achievements,
       MIN(pe.best_seconds)              AS fastest_ending_s
FROM player p
LEFT JOIN player_ending      pe ON pe.player_id = p.player_id
LEFT JOIN player_achievement pa ON pa.player_id = p.player_id
GROUP BY p.player_id, p.username;

-- ---------------------------------------------------------------------
-- Partial unique index: at most ONE equipped row per save.
-- Only rows WHERE equipped = TRUE are indexed, and the index is UNIQUE on
-- save_id, so a second equipped item in the same save is rejected.
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX one_equipped_per_save ON save_inventory (save_id) WHERE equipped;

-- ---------------------------------------------------------------------
-- PostgreSQL does not create indexes for foreign-key columns automatically.
-- These two are on the lookups the game actually performs:
--   idx_choice_from  -> loading every choice of a node (ContentRepository)
--   idx_history_save -> loading/deleting a save's history
-- (Composite primary keys that START with save_id already index the other
--  save_* child tables.)
-- ---------------------------------------------------------------------
CREATE INDEX idx_choice_from  ON choice (from_node);
CREATE INDEX idx_history_save ON save_history (save_id);
