-- =====================================================================
-- Shardbound baseline: player data only.
--
-- Game content (story, items, enemies, quests, endings) is versioned JSON in
-- content/ and loaded into memory at startup (P-04), so nothing here
-- references it with a foreign key: the server checks content ids when it
-- reads a save (SaveIncompatibleException).
--
-- Saves are one JSONB document per slot (P-05). The few columns next to the
-- document are what the slot list shows, so listing never parses JSON.
-- active_run holds every game in progress (P-22).
-- =====================================================================

CREATE TABLE player (
  player_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username   VARCHAR(30) NOT NULL UNIQUE CHECK (length(btrim(username)) > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Slot 0 is the autosave, slots 1-3 are manual saves.
-- UNIQUE (player_id, slot_no) is also the index used to list a player's slots.
CREATE TABLE save_slot (
  save_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  player_id        INT NOT NULL REFERENCES player ON DELETE CASCADE,
  slot_no          SMALLINT NOT NULL CHECK (slot_no BETWEEN 0 AND 3),
  save_version     INT NOT NULL CHECK (save_version >= 1),
  level            INT NOT NULL CHECK (level >= 1),
  area_id          VARCHAR(64) NOT NULL,
  playtime_seconds INT NOT NULL CHECK (playtime_seconds >= 0),
  data             JSONB NOT NULL CHECK (jsonb_typeof(data) = 'object'),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (player_id, slot_no)
);

-- One row per game in progress. The client's session token is never stored,
-- only its SHA-256. "version" implements optimistic locking: every write is
-- UPDATE ... WHERE version = <the version the writer read>.
CREATE TABLE active_run (
  run_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  player_id          INT NOT NULL REFERENCES player ON DELETE CASCADE,
  session_token_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(session_token_hash) = 32),
  version            BIGINT NOT NULL CHECK (version >= 1),
  save_version       INT NOT NULL CHECK (save_version >= 1),
  data               JSONB NOT NULL CHECK (jsonb_typeof(data) = 'object'),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idle runs are deleted by a daily job; this index serves that range scan.
CREATE INDEX active_run_updated_at ON active_run (updated_at);

CREATE TABLE player_achievement (
  player_id      INT NOT NULL REFERENCES player ON DELETE CASCADE,
  achievement_id VARCHAR(64) NOT NULL,
  unlocked_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (player_id, achievement_id)
);

CREATE TABLE player_ending (
  player_id        INT NOT NULL REFERENCES player ON DELETE CASCADE,
  ending_id        VARCHAR(64) NOT NULL,
  best_seconds     INT NOT NULL CHECK (best_seconds >= 0),
  first_reached_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (player_id, ending_id)
);

-- One row per player. Two LEFT JOINs on the same player multiply rows
-- (endings x achievements), so COUNT(DISTINCT ...) counts each once.
CREATE VIEW v_leaderboard AS
SELECT p.username,
       COUNT(DISTINCT pe.ending_id)      AS endings,
       COUNT(DISTINCT pa.achievement_id) AS achievements,
       MIN(pe.best_seconds)              AS fastest_ending_s
FROM player p
LEFT JOIN player_ending      pe ON pe.player_id = p.player_id
LEFT JOIN player_achievement pa ON pa.player_id = p.player_id
GROUP BY p.player_id, p.username;
