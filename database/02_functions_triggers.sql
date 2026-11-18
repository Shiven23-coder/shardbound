-- =====================================================================
-- Shardbound — 02_functions_triggers.sql
-- Run after 01_schema.sql.
--
-- Responsibility split (spec §4.5):
--   Java       -> event-driven gameplay achievements (FIRST_BLOOD, ...)
--   DB triggers-> facts about stored data: timestamps, audit, ALL_ENDINGS
-- =====================================================================

-- ---------------------------------------------------------------------
-- touch_save: keep save_slot.updated_at current on every UPDATE.
-- BEFORE trigger, so it can change NEW before the row is written.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_touch_save() RETURNS trigger AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER touch_save
BEFORE UPDATE ON save_slot
FOR EACH ROW EXECUTE FUNCTION trg_touch_save();

-- ---------------------------------------------------------------------
-- audit_save: append one save_audit row per INSERT or UPDATE of a slot.
-- TG_OP is 'INSERT' or 'UPDATE'. AFTER trigger: the row is already written.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_audit_save() RETURNS trigger AS $$
BEGIN
  INSERT INTO save_audit (save_id, action, node_id)
  VALUES (NEW.save_id, TG_OP, NEW.current_node);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_save
AFTER INSERT OR UPDATE ON save_slot
FOR EACH ROW EXECUTE FUNCTION trg_audit_save();

-- ---------------------------------------------------------------------
-- all_endings: a meta-achievement owned by the database.
-- After a player's FIRST arrival at an ending (INSERT, not the UPDATE that
-- improves best_seconds), compare how many distinct endings they have with
-- how many exist. ON CONFLICT DO NOTHING makes the grant idempotent.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_all_endings() RETURNS trigger AS $$
BEGIN
  IF (SELECT COUNT(*) FROM player_ending WHERE player_id = NEW.player_id)
     = (SELECT COUNT(*) FROM ending) THEN
    INSERT INTO player_achievement (player_id, achievement_id)
    VALUES (NEW.player_id, 'ALL_ENDINGS')
    ON CONFLICT DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER all_endings
AFTER INSERT ON player_ending
FOR EACH ROW EXECUTE FUNCTION trg_all_endings();

-- ---------------------------------------------------------------------
-- fn_best_weapon(save_id): the strongest weapon in a save.
-- ORDER BY tier DESC works because weapon_tier is an ENUM ordered F..SSS.
-- STABLE: same result for the same arguments within one statement.
-- Example: SELECT * FROM fn_best_weapon(1);
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_best_weapon(p_save INT)
RETURNS TABLE (name TEXT, tier weapon_tier, attack INT) AS $$
  SELECT i.name::text, w.tier, w.attack
  FROM save_inventory si
  JOIN weapon w ON w.item_id = si.item_id
  JOIN item   i ON i.item_id = w.item_id
  WHERE si.save_id = p_save
  ORDER BY w.tier DESC, w.attack DESC
  LIMIT 1;
$$ LANGUAGE sql STABLE;
