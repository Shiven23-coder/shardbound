import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../api/client';
import type { AttributeId, LanternMenuView, LevelPreviewView, StatsView } from '../api/types';
import { keyIntent } from '../dialogue/dialogueInput';
import type { Intent } from '../dialogue/dialogueInput';
import { useGamepadIntents } from './gamepadChoices';
import { ATTRIBUTES, BACK_ROW, CONFIRM_ROW, START, latestRequest, levelUpStep, pendingLevels, raisesOf } from '../lantern/levelUp';
import type { LevelUpState } from '../lantern/levelUp';
import { LOADOUT_START, ROOT_ITEMS, loadoutStep, placed, rootStep } from '../lantern/lanternMenu';
import type { LanternPage, LoadoutState } from '../lantern/lanternMenu';

// The Lantern menu (P-61): LEVEL UP · SKILLS · LEAVE. On the level-up page +
// and − change the pending points at once; the server's preview (before →
// after) follows a moment later, and an older answer never replaces a newer
// one. Keyboard (W/S, A/D or arrows, E, 1–3, Esc), gamepad (D-pad, A, B) and
// taps share the rules in lantern/. Every target is at least 48 px.

/** Wait this long after the last change before asking for a preview. */
const PREVIEW_DEBOUNCE_MS = 120;

interface LanternBoxProps {
  lantern: LanternMenuView;
  busy: boolean;
  layout: 'portrait' | 'landscape' | 'desktop';
  /** A one-time tip shown above the menu (the first Lantern, P-61), or null. */
  tip: string | null;
  onTipDismiss: () => void;
  onLevelUp: (raises: ReturnType<typeof raisesOf>) => void;
  onLoadout: (slots: (string | null)[]) => void;
  onLeave: () => void;
}

export default function LanternBox({ lantern, busy, layout, tip, onTipDismiss, onLevelUp, onLoadout, onLeave }: LanternBoxProps) {
  const [page, setPage] = useState<LanternPage>('root');
  const [rootHighlight, setRootHighlight] = useState(0);
  const [level, setLevel] = useState<LevelUpState>(START);
  const [loadout, setLoadout] = useState<LoadoutState>(LOADOUT_START);
  const values = useMemo(
    () => Object.fromEntries(lantern.attributes.map((a) => [a.id, a.value])) as Record<AttributeId, number>,
    [lantern.attributes],
  );
  const skills = useMemo(() => lantern.knownSkills.map((s) => s.id), [lantern.knownSkills]);
  const slots = useMemo(() => lantern.loadout.map((s) => s.skillId ?? null), [lantern.loadout]);
  const preview = useLevelPreview(level, lantern.level);

  // A bought level resets the pending points: the menu now shows the new values.
  useEffect(() => setLevel((current) => ({ ...START, row: current.row })), [lantern.level]);

  const open = (item: (typeof ROOT_ITEMS)[number]) => {
    if (item === 'leave') onLeave();
    else setPage(item);
  };

  const handle = (intent: Intent) => {
    if (busy && intent.kind === 'primary') return;
    if (page === 'root') {
      const step = rootStep(rootHighlight, intent);
      setRootHighlight(step.highlight);
      if (step.open) open(step.open);
    } else if (page === 'level') {
      const step = levelUpStep(level, intent, values, lantern.attributeMax);
      setLevel(step.state);
      if (step.action === 'confirm') onLevelUp(raisesOf(step.state));
      if (step.action === 'back') setPage('root');
    } else {
      const step = loadoutStep(loadout, intent, skills);
      setLoadout(step.state);
      if (step.action?.kind === 'set') onLoadout(placed(slots, step.action.slot, step.action.skillId));
      if (step.action?.kind === 'back') setPage('root');
    }
  };
  const latest = useRef(handle);
  latest.current = handle;

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (document.querySelector('[aria-modal="true"]')) return;
      const intent = keyIntent(event.key);
      if (!intent) return;
      event.preventDefault();          // no pause menu on Esc; the world scene ignores it
      event.stopPropagation();
      if (!event.repeat || intent.kind === 'left' || intent.kind === 'right' || intent.kind === 'up' || intent.kind === 'down') {
        latest.current(intent);
      }
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
  useGamepadIntents(useCallback((intent: Intent, held: boolean) => { if (!held) latest.current(intent); }, []));

  return (
    <section className={`lantern-box lantern-${layout} lantern-page-${page}`} aria-label={lantern.name} role="dialog">
      <header className="lantern-head">
        <h2 className="lantern-title">{lantern.name}</h2>
        <p className="lantern-summary">
          <span>Level {lantern.level}</span>
          <span className="lantern-shards">◆ {lantern.shards} Shards</span>
        </p>
      </header>
      {tip && (
        <p className="lantern-tip" role="status">
          <span>{tip}</span>
          <button type="button" className="btn btn-ghost hint-skip" aria-label="Skip this hint" onClick={onTipDismiss}>✕</button>
        </p>
      )}
      {page === 'root' && (
        <ol className="lantern-menu" aria-label="Lantern menu">
          {ROOT_ITEMS.map((item, index) => (
            <li key={item}>
              <button
                type="button"
                className={index === rootHighlight ? 'lantern-option lantern-option-highlighted' : 'lantern-option'}
                aria-current={index === rootHighlight}
                onMouseEnter={() => setRootHighlight(index)}
                onClick={() => open(item)}
              >
                <span className="lantern-option-label">{ROOT_LABELS[item]}</span>
                {item === 'level' && (
                  <span className="lantern-option-detail">Next level: {lantern.nextLevelCost} Shards</span>
                )}
              </button>
            </li>
          ))}
        </ol>
      )}
      {page === 'level' && (
        <LevelUpPage
          lantern={lantern} level={level} preview={preview} busy={busy} values={values}
          onChange={setLevel}
          onConfirm={() => onLevelUp(raisesOf(level))}
          onBack={() => setPage('root')}
        />
      )}
      {page === 'skills' && (
        <SkillsPage
          lantern={lantern} state={loadout}
          onState={setLoadout}
          onSet={(slot, skillId) => onLoadout(placed(slots, slot, skillId))}
          onBack={() => setPage('root')}
        />
      )}
    </section>
  );
}

const ROOT_LABELS = { level: 'Level up', skills: 'Skills', leave: 'Leave' } as const;

/**
 * The server's preview of the pending points, fetched a moment after the last
 * change; an answer to an older request is dropped (P-61).
 */
function useLevelPreview(level: LevelUpState, currentLevel: number): LevelPreviewView | null {
  const [preview, setPreview] = useState<LevelPreviewView | null>(null);
  const requests = useRef(latestRequest());
  const raises = raisesOf(level);
  const key = JSON.stringify(raises);
  useEffect(() => {
    const id = requests.current.next();
    if (pendingLevels(level) === 0) {
      setPreview(null);
      return undefined;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api.lanternPreview(raises, controller.signal)
        .then((answer) => { if (requests.current.isLatest(id)) setPreview(answer); })
        .catch(() => undefined);          // a dropped preview leaves the last numbers; Confirm is checked again
    }, PREVIEW_DEBOUNCE_MS);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
    // `key` stands for `raises`; the level resets the preview after a purchase.
  }, [key, currentLevel]);
  return preview;
}

interface LevelUpPageProps {
  lantern: LanternMenuView;
  level: LevelUpState;
  preview: LevelPreviewView | null;
  busy: boolean;
  values: Record<AttributeId, number>;
  onChange: (next: LevelUpState) => void;
  onConfirm: () => void;
  onBack: () => void;
}

function LevelUpPage({ lantern, level, preview, busy, values, onChange, onConfirm, onBack }: LevelUpPageProps) {
  const pending = pendingLevels(level);
  const fresh = preview !== null && preview.levelAfter === lantern.level + pending;
  const step = (row: number, kind: 'left' | 'right') => {
    onChange(levelUpStep({ ...level, row }, { kind }, values, lantern.attributeMax).state);
  };
  return (
    <div className="lantern-level" aria-label="Level up">
      <ol className="lantern-attributes">
        {ATTRIBUTES.map((attribute, row) => {
          const points = level.pending[attribute.id] ?? 0;
          return (
            <li
              key={attribute.id}
              className={row === level.row ? 'lantern-attribute lantern-attribute-highlighted' : 'lantern-attribute'}
              aria-current={row === level.row}
              onMouseEnter={() => onChange({ ...level, row })}
            >
              <span className="lantern-attribute-name">
                {attribute.name}
                <small>{attribute.effect}</small>
              </span>
              <button type="button" className="lantern-step" aria-label={`Lower ${attribute.name}`}
                disabled={points === 0} onClick={() => step(row, 'left')}>−</button>
              <span className="lantern-attribute-value" aria-label={`${attribute.name} ${values[attribute.id] + points}`}>
                {values[attribute.id] + points}
                {points > 0 && <em> +{points}</em>}
              </span>
              <button type="button" className="lantern-step" aria-label={`Raise ${attribute.name}`}
                disabled={values[attribute.id] + points >= lantern.attributeMax} onClick={() => step(row, 'right')}>+</button>
            </li>
          );
        })}
      </ol>
      <aside className="lantern-preview" aria-live="polite" aria-label="Preview">
        <PreviewRow label="Level" before={lantern.level} after={lantern.level + pending} />
        <PreviewRow label="Shards" before={lantern.shards} after={fresh ? preview.shardsAfter : null}
          warn={fresh && !preview.affordable} />
        {fresh && <p className={preview.affordable ? 'lantern-cost' : 'lantern-cost lantern-cost-short'}>Cost {preview.cost} Shards</p>}
        {STAT_ROWS.map(([label, key]) => (
          <PreviewRow key={key} label={label} before={lantern.stats[key]}
            after={pending === 0 ? lantern.stats[key] : fresh ? preview.after[key] : null} />
        ))}
      </aside>
      <div className="lantern-actions">
        <button type="button"
          className={level.row === CONFIRM_ROW ? 'btn btn-primary lantern-confirm lantern-option-highlighted' : 'btn btn-primary lantern-confirm'}
          disabled={busy || pending === 0 || (fresh && !preview.affordable)}
          onMouseEnter={() => onChange({ ...level, row: CONFIRM_ROW })}
          onClick={onConfirm}>
          Confirm{pending > 0 ? ` +${pending}` : ''}
        </button>
        <button type="button"
          className={level.row === BACK_ROW ? 'btn lantern-back lantern-option-highlighted' : 'btn lantern-back'}
          onMouseEnter={() => onChange({ ...level, row: BACK_ROW })}
          onClick={onBack}>Back</button>
      </div>
    </div>
  );
}

const STAT_ROWS: [string, keyof StatsView][] = [
  ['HP', 'maxHp'], ['FP', 'maxFp'], ['DEF', 'defence'], ['ATK', 'attack'], ['SPD', 'speed'],
];

/** "before → after"; `after` null while the server's preview is on its way. */
function PreviewRow({ label, before, after, warn = false }: { label: string; before: number; after: number | null; warn?: boolean }) {
  const changed = after !== null && after !== before;
  return (
    <p className={`lantern-preview-row${changed ? ' lantern-preview-changed' : ''}${warn ? ' lantern-preview-warn' : ''}`}>
      <span>{label}</span>
      <span>{before}</span>
      <span aria-hidden="true">→</span>
      <span>{after ?? '…'}</span>
    </p>
  );
}

interface SkillsPageProps {
  lantern: LanternMenuView;
  state: LoadoutState;
  onState: (next: LoadoutState) => void;
  onSet: (slot: number, skillId: string | null) => void;
  onBack: () => void;
}

function SkillsPage({ lantern, state, onState, onSet, onBack }: SkillsPageProps) {
  if (state.slot === null) {
    return (
      <ol className="lantern-menu lantern-slots" aria-label="Skill slots">
        {lantern.loadout.map((slot, index) => (
          <li key={slot.slot}>
            <button type="button"
              className={index === state.highlight ? 'lantern-option lantern-option-highlighted' : 'lantern-option'}
              onMouseEnter={() => onState({ ...state, highlight: index })}
              onClick={() => onState({ slot: index, highlight: 0 })}>
              <span className="lantern-option-label">{slot.name ?? 'Empty'}</span>
              <span className="lantern-option-detail">
                Slot {index + 1}{slot.name ? ` · ${slot.fpCost} FP · ${titleCase(slot.element ?? '')}` : ''}
              </span>
            </button>
          </li>
        ))}
        <li>
          <button type="button"
            className={state.highlight === lantern.loadout.length ? 'lantern-option lantern-option-highlighted' : 'lantern-option'}
            onMouseEnter={() => onState({ ...state, highlight: lantern.loadout.length })}
            onClick={onBack}>
            <span className="lantern-option-label">Back</span>
          </button>
        </li>
      </ol>
    );
  }
  const slot = state.slot;
  return (
    <ol className="lantern-menu lantern-skills" aria-label={`Skills for slot ${slot + 1}`}>
      {lantern.knownSkills.map((skill, index) => (
        <li key={skill.id}>
          <button type="button"
            className={index === state.highlight ? 'lantern-option lantern-option-highlighted' : 'lantern-option'}
            onMouseEnter={() => onState({ ...state, highlight: index })}
            onClick={() => { onState({ slot: null, highlight: slot }); onSet(slot, skill.id); }}>
            <span className="lantern-option-label">{skill.name}</span>
            <span className="lantern-option-detail">{skill.fpCost} FP · {titleCase(skill.element)} · {skill.description}</span>
          </button>
        </li>
      ))}
      <li>
        <button type="button"
          className={state.highlight === lantern.knownSkills.length ? 'lantern-option lantern-option-highlighted' : 'lantern-option'}
          onMouseEnter={() => onState({ ...state, highlight: lantern.knownSkills.length })}
          onClick={() => { onState({ slot: null, highlight: slot }); onSet(slot, null); }}>
          <span className="lantern-option-label">Empty</span>
        </button>
      </li>
    </ol>
  );
}

function titleCase(text: string): string {
  return text.charAt(0) + text.slice(1).toLowerCase();
}
