// Friendly names for the types generated from api/openapi.json (P-07).
// Never write view types by hand: change the Java records, run
// `mvnw test` (it regenerates api/openapi.json), then `npm run gen:api`.
import type { components } from './schema.gen';

type Schemas = components['schemas'];

export type SessionView = Schemas['SessionView'];
export type Mode = SessionView['mode'];
export type WorldView = Schemas['WorldView'];
export type PlayerView = Schemas['PlayerView'];
export type Facing = PlayerView['facing'];
export type NpcView = Schemas['NpcView'];
export type DialogueView = Schemas['DialogueView'];
export type ChoiceView = Schemas['ChoiceView'];
export type HudView = Schemas['HudView'];
export type WeaponInfo = Schemas['WeaponInfo'];
export type CreepView = Schemas['CreepView'];
export type BattleView = Schemas['BattleView'];
export type FighterView = Schemas['FighterView'];
export type StatusView = Schemas['StatusView'];
export type MenuView = Schemas['MenuView'];
export type SkillView = Schemas['SkillView'];
export type BagItemView = Schemas['BagItemView'];
export type HitView = Schemas['HitView'];
export type RewardsView = Schemas['RewardsView'];
export type ToastView = Schemas['ToastView'];
export type InventoryView = Schemas['InventoryView'];
export type ItemView = Schemas['ItemView'];
export type QuestView = Schemas['QuestView'];
export type ReputationView = Schemas['ReputationView'];
export type AffinityView = Schemas['AffinityView'];
export type JournalView = Schemas['JournalView'];
export type EndingView = Schemas['EndingView'];

export type ProfileResponse = Schemas['ProfileResponse'];
export type SlotView = Schemas['SlotView'];
export type AchievementView = Schemas['AchievementView'];
export type EndingInfoView = Schemas['EndingInfoView'];
export type LeaderboardView = Schemas['LeaderboardView'];
export type InspectorView = Schemas['InspectorView'];

export type BattleRequest = Schemas['BattleRequest'];
export type BattleActionKind = BattleRequest['kind'];

/** An RFC 9457 error body; `code` is stable and safe to switch on. */
export type Problem = Schemas['Problem'];

/** Where the server last saw the player; sent with WORLD_EVENT_REJECTED (§4.11). */
export interface LastPosition {
  areaId: string;
  x: number;
  y: number;
  facing: Facing;
}
