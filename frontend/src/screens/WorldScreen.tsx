import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import { useGame } from '../state/GameContext';
import type { BattleView, LastPosition, SessionView } from '../api/types';
import { WorldBridge } from '../world/bridge';
import WorldCanvas from '../world/WorldCanvas';
import { useInputState } from '../input/inputMode';
import { audio, audioPacks } from '../audio/audio';
import type { Layout } from '../input/inputMode';
import Hud from '../components/Hud';
import HudCluster from '../components/HudCluster';
import CurrencyToast from '../components/CurrencyToast';
import HintBubble, { useHint } from '../components/HintBubble';
import GameMenu from '../components/GameMenu';
import ActionButton from '../components/ActionButton';
import AreaTitle, { useAreaTitle } from '../components/AreaTitle';
import ObjectiveLine, { useObjectiveBanner } from '../components/ObjectiveLine';
import { overlayNow } from '../components/overlays';
import DialogueBox from '../components/DialogueBox';
import TouchOverlay, { Dpad, InteractButton } from '../components/TouchControls';
import FullscreenButton from '../components/FullscreenButton';
import { enterFullscreen, fullscreenSupported, isFullscreen, useFullscreen } from '../ui/fullscreen';
import InventoryPanel, { bagRows } from '../components/InventoryPanel';
import { BAG_START, bagStep, type BagState } from '../bag/bagMenu';
import { keyIntent, type Intent } from '../dialogue/dialogueInput';
import { useGamepadIntents } from '../components/gamepadChoices';
import QuestLog from '../components/QuestLog';
import ReputationPanel from '../components/ReputationPanel';
import Journal from '../components/Journal';
import BattleBox from '../components/BattleBox';
import BattleInfoBox from '../components/BattleInfoBox';
import BossBar from '../components/BossBar';
import BossBanner from '../components/BossBanner';
import { CutsceneLine, SkipButton } from '../components/CutsceneControls';
import { useCutscenePlayer } from '../cutscene/useCutscenePlayer';
import { bossIdOf } from '../world/content';
import LanternBox from '../components/LanternBox';
import ShardsCounter from '../components/ShardsCounter';
import { hintText, markHintSeen, seenHints } from '../settings/hints';
import { useSettings } from '../settings/settings';
import { hpShown } from '../battle/battleText';

const TABS = ['Inventory', 'Quests', 'Reputation', 'Journal'] as const;
type Tab = (typeof TABS)[number];

/** The share of the game view an open dialogue covers, per layout: in portrait it sits in the deck. */
const DIALOGUE_COVER: Record<Layout, number> = { desktop: 1 / 3, landscape: 1 / 3, portrait: 0 };
/** The share of the game view the battle text box covers, per layout (P-57): in portrait it sits in the deck. */
const BATTLE_COVER: Record<Layout, number> = { desktop: 0.3, landscape: 0.36, portrait: 0 };

// The game, in the layout that suits the input in use (P-40):
// - desktop (keyboard, mouse, gamepad): the world fills the screen, no touch controls;
// - phone landscape: the same, plus translucent touch controls over the bottom corners;
// - phone portrait: the world on top, a control deck below (D-pad, Ⓐ, Bag, Menu),
//   and the dialogue inside the deck while one is open.
// The compact HUD sits top-left over the world and never hides the player (P-39).
// A cutscene (P-68) hides the HUD and the controls: its lines take the
// dialogue's place, and a held Skip button sits top-right.
export default function WorldScreen() {
  const { state, run } = useGame();
  const { view, busy } = state;
  const bridge = useMemo(() => new WorldBridge(), []);
  const { layout, device } = useInputState();
  const { hints: hintsOn } = useSettings();
  const playerId = state.player?.playerId;
  // The first Lantern menu explains levelling and resting, once per profile (P-61).
  const [levelTip, setLevelTip] = useState(false);
  const levelTipShown = useRef(false);
  levelTipShown.current = levelTip;
  const atLantern = view?.mode === 'LANTERN';
  useEffect(() => {
    if (atLantern && hintsOn && playerId !== undefined && !seenHints(playerId).has('levelup')) setLevelTip(true);
    if (!atLantern && levelTipShown.current && playerId !== undefined) {
      markHintSeen(playerId, 'levelup');                   // shown once: leaving the menu counts as read
      setLevelTip(false);
    }
  }, [atLantern, hintsOn, playerId]);
  const dismissLevelTip = () => {
    if (playerId !== undefined) markHintSeen(playerId, 'levelup');
    setLevelTip(false);
  };
  // Lantern and Lost Shards sounds (P-61, P-62): a rest, a bought level, Shards picked up.
  const heard = useRef({ atLantern: false, level: 0, lost: false });
  useEffect(() => {
    if (!view) return;
    const before = heard.current;
    const level = view.lantern?.level ?? 0;
    const lost = Boolean(view.world.lostShards);
    if (atLantern && !before.atLantern) audio.play('lanternRest');
    else if (atLantern && level > before.level && before.level > 0) audio.play('levelUp');
    if (before.lost && !lost && view.mode === 'WORLD') audio.play('shardsRecovered');
    heard.current = { atLantern, level, lost };
  }, [view, atLantern]);
  const [panelOpen, setPanelOpen] = useState(false);
  const [hudCovered, setHudCovered] = useState(false);
  const hudRef = useRef<HTMLButtonElement>(null);
  const viewRef = useRef<HTMLDivElement>(null);
  const fullscreen = useFullscreen();
  const fullscreenAsked = useRef(false);
  // A battle stays on screen from its first round until the player has read the round that ended it.
  const [battle, setBattle] = useState<BattleView | null>(null);
  useEffect(() => {
    if (view?.battle) setBattle(view.battle);
  }, [view?.battle]);
  const walking = view?.mode === 'WORLD' && battle === null;
  const hint = useHint(bridge, state.player?.playerId, walking);
  // One overlay at a time (P-49): the area title, then any opening dialogue, then the objective, then a hint.
  const titleShowing = useAreaTitle(view?.world.areaName ?? '');
  const dialogueOpen = view?.mode === 'DIALOGUE' && Boolean(view.dialogue);
  const objective = useObjectiveBanner(view?.objective, !titleShowing && !dialogueOpen);
  const overlay = overlayNow({ battle: battle !== null, title: titleShowing, dialogue: dialogueOpen, objective: objective.waiting, hint: hint.shown !== null });

  useEffect(() => {
    bridge.device = device;
  }, [bridge, device]);

  // A cutscene plays while the server is in CUTSCENE mode, once any battle before it has been read to its end
  // (a boss's epilogue follows its last page); played out or skipped, it ends with a keyed request.
  const cutscene = view?.mode === 'CUTSCENE' && battle === null ? view.cutscene ?? null : null;
  const cutscenePlayer = useCutscenePlayer(cutscene, bridge, (id) => void run((key) => api.finishCutscene(id, key)));

  // I opens and closes the Bag (P-47), except while typing, with a menu panel open, in a battle (it has its BAG)
  // or in a cutscene.
  const inBattle = useRef(false);
  inBattle.current = battle !== null || cutscene !== null;
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'i' && event.key !== 'I') return;
      if (inBattle.current) return;
      if (event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return;
      const target = event.target as HTMLElement | null;
      if (target && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))) return;
      if (document.querySelector('[aria-modal="true"]')) return;
      setPanelOpen((open) => !open);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // The server's world goes to the scene; walking pauses outside WORLD mode, while a request is out and while
  // the Bag is open (its keys move the highlight, not the player).
  useEffect(() => {
    if (view) bridge.sendView(view.world);
  }, [bridge, view]);
  useEffect(() => {
    bridge.setFrozen(view?.mode !== 'WORLD' || busy || battle !== null || panelOpen);
  }, [bridge, view?.mode, busy, battle, panelOpen]);
  useEffect(() => {
    bridge.setBattle(battle, BATTLE_COVER[layout]);
  }, [bridge, battle, layout]);
  // The text line on screen: the arena plays its hits and the HP bars drain with them.
  const [battleLine, setBattleLine] = useState({ round: -1, line: 0 });
  const showBattleLine = useCallback((round: number, line: number) => {
    bridge.setBattleLine(round, line);
    setBattleLine({ round, line });
  }, [bridge]);
  const shownLine = battle && battleLine.round === battle.round ? battleLine.line : 0;
  useEffect(() => {
    bridge.setDialogue(view?.mode === 'DIALOGUE' ? DIALOGUE_COVER[layout] : null);
  }, [bridge, view?.mode, layout]);

  // The region's music plays while the world is on screen (P-42), the battle track during a battle (P-57),
  // a boss's own track against it, and a cutscene's track once it asks for one (P-67).
  const bossId = battle ? bossIdOf(battle.enemy.sprite) : null;
  const music = battle
    ? (bossId ? audioPacks.bosses[bossId]?.music : undefined) ?? 'battle'
    : cutscenePlayer.music ?? view?.world.regionId;
  useEffect(() => {
    audio.setMusic(music ?? null);
  }, [music]);
  useEffect(() => () => audio.setMusic(null), []);
  const regionId = view?.world.regionId;
  useEffect(() => {
    if (regionId) audio.loadRegion(regionId);
  }, [regionId]);
  // A boss's pack loads once it can be met: on its map, in a cutscene or in its battle.
  const bossesNear = [
    ...(view?.world.bosses ?? []).map((boss) => boss.bossId),
    ...(cutscene?.actors ?? []).map((actor) => bossIdOf(actor.sprite)),
    bossId,
  ].filter((id): id is string => Boolean(id)).join(' ');
  useEffect(() => {
    bossesNear.split(' ').filter(Boolean).forEach((id) => audio.loadBoss(id));
  }, [bossesNear]);

  // World events from the scene go to the server; a rejection puts the player back (§4.11).
  // An event lost to an outage is sent again with its key once the game reconnects (P-56).
  useEffect(() => {
    const snapBack = { onRejected: (position: LastPosition) => bridge.toScene.emit('snapBack', position) };
    const offs = [
      bridge.toReact.on('exit', ({ exitId, x, y, facing }) =>
        void run((key) => api.worldExit(exitId, x, y, facing, key), snapBack)),
      bridge.toReact.on('interact', ({ targetId, x, y, facing }) =>
        void run((key) => api.worldInteract(targetId, x, y, facing, key), snapBack)),
      bridge.toReact.on('recover', ({ x, y, facing }) =>
        void run((key) => api.worldRecover(x, y, facing, key), snapBack)),
      bridge.toReact.on('encounter', ({ creepId, x, y, facing }) =>
        void run((key) => api.worldEncounter(creepId, x, y, facing, key), snapBack)),
      bridge.toReact.on('creepNear', () => audio.loadBattle()),
      bridge.toReact.on('hudCovered', setHudCovered),
    ];
    return () => offs.forEach((off) => off());
  }, [bridge, run]);

  // The scene keeps the player out from under the HUD: tell it where the HUD is.
  useEffect(() => {
    const hudElement = hudRef.current;
    const viewElement = viewRef.current;
    if (!hudElement || !viewElement) return undefined;
    const measure = () => {
      const hudBox = hudElement.getBoundingClientRect();
      const viewBox = viewElement.getBoundingClientRect();
      bridge.hudArea = { x: hudBox.left - viewBox.left, y: hudBox.top - viewBox.top, width: hudBox.width, height: hudBox.height };
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(hudElement);
    observer.observe(viewElement);
    return () => observer.disconnect();
  }, [bridge, layout, view !== null]);

  if (!view) return null;

  // On a phone the first tap in the game asks for full screen (P-53), once: if the player leaves
  // it, the full-screen button brings it back.
  function autoFullscreen() {
    if (layout === 'desktop' || fullscreenAsked.current || !fullscreenSupported() || isFullscreen()) return;
    fullscreenAsked.current = true;
    enterFullscreen();
  }

  const dialogue = overlay === 'dialogue' && view.dialogue
    ? (placement: 'overlay' | 'deck') => (
      <DialogueBox dialogue={view.dialogue!} busy={busy} placement={placement} onChoose={(id) => run((key) => api.choose(id, key))} />
    )
    : null;
  const actionVariant = layout === 'desktop' ? 'icon' : 'pill';
  const fullscreenButton = layout !== 'desktop' && fullscreenSupported() && !fullscreen ? <FullscreenButton /> : null;
  const bagButton = (
    <ActionButton label="Bag" icon="bag" keyHint="I" variant={actionVariant} expanded={panelOpen} onClick={() => setPanelOpen(!panelOpen)} />
  );
  const cutsceneLine = cutscenePlayer.line
    ? (placement: 'overlay' | 'deck') => (
      <CutsceneLine
        line={cutscenePlayer.line!} typed={cutscenePlayer.typed} placement={placement}
        onTyped={cutscenePlayer.onTyped} onAdvance={cutscenePlayer.advance}
      />
    )
    : null;
  // A telegraphed blow is warned of once the round's text is read, until it lands (P-67).
  const battleRead = shownLine === Infinity;
  const warning = battle && battle.telegraphMove && battleRead && !battle.outcome ? battle.telegraphMove : null;
  const boss = battle?.boss;
  const bossBar = battle && boss ? (
    <BossBar
      boss={boss}
      fighter={{ ...battle.enemy, hp: hpShown(battle.enemy, false, battle.hits, shownLine) }}
      phase={boss.phaseLine != null && shownLine < boss.phaseLine ? 1 : boss.phase}
      warning={warning}
    />
  ) : null;
  const lastBlow = battle?.hits.at(-1)?.line ?? -1;
  const vanquished = Boolean(boss && battle?.outcome === 'WON' && shownLine > lastBlow);
  const battleBox = battle
    ? (placement: 'overlay' | 'deck') => (
      <BattleBox
        battle={battle} busy={busy} placement={placement}
        onAction={(action) => run((key) => api.battleAction(action, key))}
        onDone={() => setBattle(null)}
        onLine={showBattleLine}
      />
    )
    : null;

  return (
    <div
      className={`screen world-screen layout-${layout}${battle ? ' in-battle' : ''}`}
      data-layout={layout}
      data-area-name={view.world.areaName}
      onPointerDownCapture={autoFullscreen}
      onContextMenu={(event) => event.preventDefault()}
    >
      <div ref={viewRef} className="world-view">
        <WorldCanvas bridge={bridge} />
        {battle && (
          <div className="battle-hud">
            {!boss && (
              <BattleInfoBox
                fighter={{ ...battle.enemy, hp: hpShown(battle.enemy, false, battle.hits, shownLine) }} side="enemy" warning={warning}
              />
            )}
            {layout !== 'portrait' && bossBar}
            <BattleInfoBox
              fighter={{ ...battle.player, hp: hpShown(battle.player, true, battle.hits, shownLine) }}
              side="player" fp={battle.menu.fp} maxFp={battle.menu.maxFp}
            />
          </div>
        )}
        {boss && <BossBanner kind="name" text={battle?.round === 1 ? boss.name : null} />}
        {boss && <BossBanner kind="vanquished" text={vanquished ? 'Foe vanquished' : null} />}
        {battle && layout !== 'portrait' && battleBox?.('overlay')}
        {!battle && !cutscene && <ShardsCounter shards={view.hud.shards} />}
        {!battle && !cutscene && <HudCluster ref={hudRef} hud={view.hud} alwaysNumbers={layout === 'desktop'} dodge={hudCovered} />}
        {overlay === 'title' && <AreaTitle name={view.world.areaName} />}
        {overlay === 'objective' && objective.shown && <ObjectiveLine objective={objective.shown} />}
        {layout === 'portrait' && fullscreenButton && <div className="world-actions">{fullscreenButton}</div>}
        {cutscene && (
          <SkipButton
            hold={cutscenePlayer.hold} hinted={cutscenePlayer.hinted} keyHint={device === 'gamepad' ? 'Ⓐ' : device === 'keyboard' ? 'E' : undefined}
            onPress={cutscenePlayer.press} onRelease={() => cutscenePlayer.release(true)}
          />
        )}
        {layout !== 'portrait' && cutsceneLine?.('overlay')}
        {layout !== 'portrait' && !battle && !cutscene && (
          <div className="world-actions">
            {fullscreenButton}
            {bagButton}
            <GameMenu variant={actionVariant} objective={view.objective} />
          </div>
        )}
        {overlay === 'hint' && hint.shown && <HintBubble {...hint.shown} device={device} onSkip={hint.skip} />}
        <CurrencyToast amount={view.hud.gold} label="gold" />
        {layout !== 'portrait' && dialogue?.('overlay')}
        {layout === 'landscape' && walking && <TouchOverlay bridge={bridge} />}
      </div>
      {layout === 'portrait' && (
        <div className="control-deck">
          {bossBar}
          {battleBox?.('deck') ?? dialogue?.('deck') ?? (cutscene ? cutsceneLine?.('deck') : (
            <>
              <Dpad bridge={bridge} />
              <div className="deck-buttons">
                {bagButton}
                <GameMenu placement="up" variant={actionVariant} objective={view.objective} />
              </div>
              <InteractButton bridge={bridge} />
            </>
          ))}
        </div>
      )}
      {view.mode === 'LANTERN' && view.lantern && (
        <LanternBox
          lantern={view.lantern}
          busy={busy}
          layout={layout}
          tip={levelTip ? hintText('levelup', device) : null}
          onTipDismiss={dismissLevelTip}
          onLevelUp={(raises) => run((key) => api.lanternLevelUp(raises, key))}
          onLoadout={(slots) => run((key) => api.lanternLoadout(slots, key))}
          onLeave={() => run((key) => api.lanternLeave(key))}
        />
      )}
      {panelOpen && (
        <SidePanels
          view={view}
          busy={busy}
          onClose={() => setPanelOpen(false)}
          onEquip={(id) => run((key) => api.equip(id, key))}
          onUse={(id) => run((key) => api.useItem(id, key))}
        />
      )}
    </div>
  );
}

interface SidePanelsProps {
  view: SessionView;
  busy: boolean;
  onClose: () => void;
  onEquip: (itemId: string) => void;
  onUse: (itemId: string) => void;
}

// The Bag: the full character sheet (HP, FP, Shards, gold, weapon) above the
// inventory, quests, reputation and journal. On the Inventory tab the keyboard
// and gamepad move a highlight and open Compare (P-65); left and right change tabs.
function SidePanels({ view, busy, onClose, onEquip, onUse }: SidePanelsProps) {
  const [tab, setTab] = useState<Tab>(TABS[0]);
  const [bag, setBag] = useState<BagState>(BAG_START);
  const rows = bagRows(view.inventory);

  const handle = (intent: Intent) => {
    if (tab !== 'Inventory') {
      if (intent.kind === 'back') onClose();
      if (intent.kind === 'left' || intent.kind === 'right') {
        setTab(TABS[(TABS.indexOf(tab) + (intent.kind === 'left' ? -1 : 1) + TABS.length) % TABS.length]!);
      }
      return;
    }
    const step = bagStep(bag, intent, rows);
    setBag(step.state);
    const action = step.action;
    if (action?.kind === 'equip' && !busy) onEquip(action.itemId);
    if (action?.kind === 'use' && !busy) onUse(action.itemId);
    if (action?.kind === 'close') onClose();
    if (action?.kind === 'tab') setTab(TABS[(TABS.indexOf(tab) + action.delta + TABS.length) % TABS.length]!);
  };
  const latest = useRef(handle);
  latest.current = handle;

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (document.querySelector('[aria-modal="true"]')) return;
      const intent = keyIntent(event.key);
      if (!intent) return;
      event.preventDefault();          // Esc closes the Bag here, not the pause menu; E does not act in the world
      event.stopPropagation();
      if (!event.repeat || intent.kind === 'up' || intent.kind === 'down') latest.current(intent);
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
  useGamepadIntents(useCallback((intent: Intent, held: boolean) => { if (!held) latest.current(intent); }, []));

  // The highlighted row stays in view as it moves through a long Bag; Compare opens at its top,
  // so both cards' names show even on a short landscape screen.
  const comparing = bag.page === 'compare';
  useEffect(() => {
    if (comparing) document.querySelector('.side-panels .weapon-compare')?.scrollIntoView({ block: 'start' });
  }, [comparing]);
  useEffect(() => {
    if (bag.page === 'list') document.querySelector('.side-panels .bag-highlighted')?.scrollIntoView({ block: 'nearest' });
  }, [bag]);

  return (
    <aside className={comparing ? 'side-panels side-panels-comparing panel' : 'side-panels panel'} aria-label="Character">
      <button type="button" className="side-panels-close" aria-label="Close" onClick={onClose}>
        ✕
      </button>
      <div className="side-panels-body">
        <Hud hud={view.hud} demoMode={view.demoMode} />
        <div className="tabs" role="tablist" aria-label="Character panels">
          {TABS.map((name) => (
            <button
              key={name}
              type="button"
              role="tab"
              id={`tab-${name}`}
              aria-selected={tab === name}
              aria-controls="side-tabpanel"
              className={tab === name ? 'tab tab-active' : 'tab'}
              onClick={() => {
                setTab(name);
                setBag(BAG_START);
              }}
            >
              {name}
            </button>
          ))}
        </div>
        <div id="side-tabpanel" role="tabpanel" aria-labelledby={`tab-${tab}`} className="tab-panel">
          {tab === 'Inventory' && (
            <InventoryPanel
              inventory={view.inventory}
              equipped={view.hud?.weapon ?? null}
              state={bag}
              busy={busy}
              onCompare={(itemId) => setBag({ page: 'compare', highlight: 0, itemId })}
              onEquip={(itemId) => {
                onEquip(itemId);
                setBag({ page: 'list', highlight: Math.max(0, rows.findIndex((r) => r.id === itemId)) });
              }}
              onUse={onUse}
              onBack={() => setBag({ page: 'list', highlight: Math.max(0, rows.findIndex((r) => bag.page === 'compare' && r.id === bag.itemId)) })}
            />
          )}
          {tab === 'Quests' && <QuestLog quests={view.quests ?? []} />}
          {tab === 'Reputation' && (
            <ReputationPanel reputation={view.reputation ?? []} affinity={view.affinity ?? []} />
          )}
          {tab === 'Journal' && <Journal journal={view.journal ?? []} />}
        </div>
      </div>
    </aside>
  );
}
