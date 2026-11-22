import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import { useGame } from '../state/GameContext';
import type { LastPosition, SessionView } from '../api/types';
import { WorldBridge } from '../world/bridge';
import WorldCanvas from '../world/WorldCanvas';
import { useInputState } from '../input/inputMode';
import { audio } from '../audio/audio';
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
import InventoryPanel from '../components/InventoryPanel';
import QuestLog from '../components/QuestLog';
import ReputationPanel from '../components/ReputationPanel';
import Journal from '../components/Journal';
import CombatScreen from './CombatScreen';

const TABS = ['Inventory', 'Quests', 'Reputation', 'Journal'] as const;
type Tab = (typeof TABS)[number];

/** The share of the game view an open dialogue covers, per layout: in portrait it sits in the deck. */
const DIALOGUE_COVER: Record<Layout, number> = { desktop: 1 / 3, landscape: 1 / 3, portrait: 0 };

// The game, in the layout that suits the input in use (P-40):
// - desktop (keyboard, mouse, gamepad): the world fills the screen, no touch controls;
// - phone landscape: the same, plus translucent touch controls over the bottom corners;
// - phone portrait: the world on top, a control deck below (D-pad, Ⓐ, Bag, Menu),
//   and the dialogue inside the deck while one is open.
// The compact HUD sits top-left over the world and never hides the player (P-39).
export default function WorldScreen() {
  const { state, run } = useGame();
  const { view, busy } = state;
  const bridge = useMemo(() => new WorldBridge(), []);
  const { layout, device } = useInputState();
  const [panelOpen, setPanelOpen] = useState(false);
  const [hudCovered, setHudCovered] = useState(false);
  const hudRef = useRef<HTMLButtonElement>(null);
  const viewRef = useRef<HTMLDivElement>(null);
  const fullscreen = useFullscreen();
  const fullscreenAsked = useRef(false);
  const walking = view?.mode === 'WORLD';
  const hint = useHint(bridge, state.player?.playerId, walking);
  // One overlay at a time (P-49): the area title, then any opening dialogue, then the objective, then a hint.
  const titleShowing = useAreaTitle(view?.world.areaName ?? '');
  const dialogueOpen = view?.mode === 'DIALOGUE' && Boolean(view.dialogue);
  const objective = useObjectiveBanner(view?.objective, !titleShowing && !dialogueOpen);
  const overlay = overlayNow({ title: titleShowing, dialogue: dialogueOpen, objective: objective.waiting, hint: hint.shown !== null });

  useEffect(() => {
    bridge.device = device;
  }, [bridge, device]);

  // I opens and closes the Bag (P-47), except while typing or with a menu panel open.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'i' && event.key !== 'I') return;
      if (event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return;
      const target = event.target as HTMLElement | null;
      if (target && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))) return;
      if (document.querySelector('[aria-modal="true"]')) return;
      setPanelOpen((open) => !open);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // The server's world goes to the scene; walking pauses outside WORLD mode and while a request is out.
  useEffect(() => {
    if (view) bridge.sendView(view.world);
  }, [bridge, view]);
  useEffect(() => {
    bridge.setFrozen(view?.mode !== 'WORLD' || busy);
  }, [bridge, view?.mode, busy]);
  useEffect(() => {
    bridge.setDialogue(view?.mode === 'DIALOGUE' ? DIALOGUE_COVER[layout] : null);
  }, [bridge, view?.mode, layout]);

  // The region's music plays while the world is on screen (P-42).
  const regionId = view?.world.regionId;
  useEffect(() => {
    audio.setMusic(regionId ?? null);
    return () => audio.setMusic(null);
  }, [regionId]);

  // World events from the scene go to the server; a rejection puts the player back (§4.11), and so
  // does an event lost to an outage, once the game reconnects: where the server last saw them.
  useEffect(() => {
    const snapBack = {
      onRejected: (position: LastPosition) => bridge.toScene.emit('snapBack', position),
      onResumed: ({ world }: SessionView) => bridge.toScene.emit('snapBack', { areaId: world.areaId, ...world.player }),
    };
    const offs = [
      bridge.toReact.on('exit', ({ exitId, x, y, facing }) =>
        void run(() => api.worldExit(exitId, x, y, facing), snapBack)),
      bridge.toReact.on('interact', ({ targetId, x, y, facing }) =>
        void run(() => api.worldInteract(targetId, x, y, facing), snapBack)),
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
      <DialogueBox dialogue={view.dialogue!} busy={busy} placement={placement} onChoose={(id) => run(() => api.choose(id))} />
    )
    : null;
  const actionVariant = layout === 'desktop' ? 'icon' : 'pill';
  const fullscreenButton = layout !== 'desktop' && fullscreenSupported() && !fullscreen ? <FullscreenButton /> : null;
  const bagButton = (
    <ActionButton label="Bag" icon="bag" keyHint="I" variant={actionVariant} expanded={panelOpen} onClick={() => setPanelOpen(!panelOpen)} />
  );

  return (
    <div
      className={`screen world-screen layout-${layout}`}
      data-layout={layout}
      data-area-name={view.world.areaName}
      onPointerDownCapture={autoFullscreen}
      onContextMenu={(event) => event.preventDefault()}
    >
      <div ref={viewRef} className="world-view">
        <WorldCanvas bridge={bridge} />
        <HudCluster ref={hudRef} hud={view.hud} alwaysNumbers={layout === 'desktop'} dodge={hudCovered} />
        {overlay === 'title' && <AreaTitle name={view.world.areaName} />}
        {overlay === 'objective' && objective.shown && <ObjectiveLine objective={objective.shown} />}
        {layout === 'portrait' && fullscreenButton && <div className="world-actions">{fullscreenButton}</div>}
        {layout !== 'portrait' && (
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
          {dialogue?.('deck') ?? (
            <>
              <Dpad bridge={bridge} />
              <div className="deck-buttons">
                {bagButton}
                <GameMenu placement="up" variant={actionVariant} objective={view.objective} />
              </div>
              <InteractButton bridge={bridge} />
            </>
          )}
        </div>
      )}
      {view.mode === 'COMBAT' && (
        <div className="combat-overlay">
          <CombatScreen />
        </div>
      )}
      {panelOpen && (
        <SidePanels
          view={view}
          busy={busy}
          onClose={() => setPanelOpen(false)}
          onEquip={(id) => run(() => api.equip(id))}
          onUse={(id) => run(() => api.useItem(id))}
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

// The Bag: the full character sheet (HP, XP, energy, gold, weapon) above the
// inventory, quests, reputation and journal. Quick slots join it on touch (V4).
function SidePanels({ view, busy, onClose, onEquip, onUse }: SidePanelsProps) {
  const [tab, setTab] = useState<Tab>(TABS[0]);

  return (
    <aside className="side-panels panel" aria-label="Character">
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
            onClick={() => setTab(name)}
          >
            {name}
          </button>
        ))}
        <button type="button" className="tab" aria-label="Close" onClick={onClose}>
          ✕
        </button>
      </div>
      <div id="side-tabpanel" role="tabpanel" aria-labelledby={`tab-${tab}`} className="tab-panel">
        {tab === 'Inventory' && (
          <InventoryPanel inventory={view.inventory} busy={busy} onEquip={onEquip} onUse={onUse} />
        )}
        {tab === 'Quests' && <QuestLog quests={view.quests ?? []} />}
        {tab === 'Reputation' && (
          <ReputationPanel reputation={view.reputation ?? []} affinity={view.affinity ?? []} />
        )}
        {tab === 'Journal' && <Journal journal={view.journal ?? []} />}
      </div>
    </aside>
  );
}
