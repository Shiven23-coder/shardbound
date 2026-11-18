-- =====================================================================
-- Shardbound — 99_reset.sql
-- Drops EVERYTHING created by 01–03 (tables, views, functions, types),
-- including all player data. Use it to start over:
--   psql -U postgres -d shardbound -f database/99_reset.sql
-- then run 01, 02, 03 again and re-import content.
-- Order: view first, then tables (CASCADE also removes their triggers and
-- indexes), then functions, then types (types last: tables use them).
-- =====================================================================

DROP VIEW IF EXISTS v_leaderboard;

DROP TABLE IF EXISTS
  save_audit,
  player_ending,
  player_achievement,
  save_history,
  save_quest,
  save_affinity,
  save_reputation,
  save_flag,
  save_inventory,
  save_slot,
  player,
  ending_condition,
  ending,
  achievement,
  quest_prereq,
  quest,
  choice_effect,
  choice_condition,
  choice,
  story_node,
  encounter_enemy,
  encounter,
  enemy,
  consumable,
  weapon,
  item,
  npc,
  location,
  faction
CASCADE;

DROP FUNCTION IF EXISTS trg_touch_save();
DROP FUNCTION IF EXISTS trg_audit_save();
DROP FUNCTION IF EXISTS trg_all_endings();
DROP FUNCTION IF EXISTS fn_best_weapon(INT);

DROP TYPE IF EXISTS quest_status;
DROP TYPE IF EXISTS item_type;
DROP TYPE IF EXISTS node_type;
DROP TYPE IF EXISTS weapon_tier;
