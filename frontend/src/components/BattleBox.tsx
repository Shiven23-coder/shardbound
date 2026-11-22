import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import Typewriter from './Typewriter';
import { useGamepadIntents } from './gamepadChoices';
import { keyIntent } from '../dialogue/dialogueInput';
import type { Intent } from '../dialogue/dialogueInput';
import { splitPages } from '../dialogue/pages';
import { menuItems, menuStep, ROOT } from '../battle/battleMenu';
import type { MenuState } from '../battle/battleMenu';
import { readingAt, roundPages, TEXT_TIMING, textStep } from '../battle/battleText';
import type { Reading, RoundPage } from '../battle/battleText';
import { useSettings } from '../settings/settings';
import type { BattleRequest, BattleView } from '../api/types';

// The battle's text box and menu (P-57). A round's lines show one page at a
// time at the Battle text speed and move on by themselves; a tap, E or A skips
// the rest of the round, and holding E steps through it. Then the menu:
// ATTACK · SKILLS · BAG · FLEE in a 2 × 2 grid, with Strike/Guard, the four
// skill slots and the bag as pages. W/S, the D-pad or stick move the highlight,
// E/A/Enter or 1–4 take an entry, Esc, Backspace or B go back. When the battle
// has ended, the last page is followed by Continue.
/** Lines per page at most, as dialogue (P-50). */
const MAX_LINES = { overlay: 2, deck: 3 } as const;

interface BattleBoxProps {
  battle: BattleView;
  busy: boolean;
  placement: 'overlay' | 'deck';
  onAction: (action: BattleRequest) => void;
  /** The ended battle's last page was read: back to the world. */
  onDone: () => void;
  /** The text line now on screen (Infinity once the round's text is done): the arena plays its hits. */
  onLine: (round: number, line: number) => void;
}

export default function BattleBox({ battle, busy, placement, onAction, onDone, onLine }: BattleBoxProps) {
  const { battleTextSpeed } = useSettings();
  const timing = TEXT_TIMING[battleTextSpeed];
  const lines = battle.text;
  const [pages, setPages] = useState<RoundPage[]>(() => lines.map((text, line) => ({ text, line })));
  // Where the reading is, per round: a new round starts on its first page in
  // its very first render, so the arena never sees the last round's "all read".
  const roundKey = `${battle.round}:${battle.outcome ?? ''}`;
  const [reading, setReading] = useState<Reading>({ key: roundKey, page: 0, typed: false });
  const { page, typed } = readingAt(reading, roundKey);
  const setPage = (next: (current: number) => number) =>
    setReading((r) => ({ key: roundKey, page: next(readingAt(r, roundKey).page), typed: false }));
  const setTyped = () => setReading((r) => ({ ...readingAt(r, roundKey), typed: true }));
  const [menu, setMenu] = useState<MenuState>(ROOT);
  const measure = useRef<HTMLDivElement>(null);
  const textDone = page >= pages.length;
  const ended = Boolean(battle.outcome);
  const items = useMemo(() => menuItems(menu.page, battle.menu), [menu.page, battle.menu]);

  // A new round starts the menu on ATTACK.
  useEffect(() => {
    setMenu(ROOT);
  }, [roundKey]);

  // Lines that do not fit the box are split by the dialogue pager.
  useLayoutEffect(() => {
    const element = measure.current;
    if (!element) {
      setPages(lines.map((text, line) => ({ text, line })));
      return;
    }
    const lineHeight = parseFloat(getComputedStyle(element).lineHeight);
    const fits = (candidate: string) => {
      element.textContent = candidate;
      return element.scrollHeight <= MAX_LINES[placement] * lineHeight + 1;
    };
    setPages(roundPages(lines, (line) => splitPages(line, fits)));
  }, [lines, placement]);

  // The arena keeps pace with the text: it plays a line's hits when that line shows.
  const shownLine = textDone ? Infinity : (pages[page]?.line ?? 0);
  useEffect(() => {
    onLine(battle.round, shownLine);
  }, [onLine, battle.round, shownLine]);

  // A typed page moves on by itself after a pause.
  useEffect(() => {
    if (!typed || textDone) return undefined;
    const timer = setTimeout(() => {
      setPage((current) => current + 1);
    }, timing.pauseMs);
    return () => clearTimeout(timer);
  }, [typed, textDone, timing.pauseMs, page, roundKey]);

  const act = (action: BattleRequest | undefined) => {
    if (action && !busy) onAction(action);
  };

  const handle = (intent: Intent, held: boolean) => {
    if (!textDone) {
      if (intent.kind !== 'primary') return;
      const step = textStep(held, page >= pages.length - 1);
      if (step?.kind === 'skip') setPage(() => pages.length);
      if (step?.kind === 'next') setPage((current) => current + 1);
      return;
    }
    if (ended) {
      if (intent.kind === 'primary' && !held) onDone();
      return;
    }
    if (held) return;
    const step = menuStep(menu, battle.menu, intent);
    setMenu(step.state);
    act(step.action);
  };
  const latest = useRef(handle);
  latest.current = handle;

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (document.querySelector('[aria-modal="true"]')) return;
      const intent = keyIntent(event.key);
      if (!intent) return;
      event.preventDefault();          // no pause menu on Esc, no native click, and the world scene ignores it
      event.stopPropagation();
      latest.current(intent, event.repeat);
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
  useGamepadIntents(useCallback((intent: Intent, held: boolean) => latest.current(intent, held), []));

  const highlight = Math.min(menu.highlight, items.length - 1);
  const showMenu = textDone && !ended;

  return (
    <section className={`battle-box battle-${placement}`} aria-label="Battle">
      <div className="battle-text" onClick={() => handle({ kind: 'primary' }, false)} role="presentation">
        <div className="battle-measure" ref={measure} aria-hidden="true" />
        {!textDone && (
          <Typewriter
            key={`${battle.round}:${page}:${pages[page]?.text}`}
            text={pages[page]?.text}
            charsPerTick={timing.charsPerTick}
            onDone={setTyped}
          />
        )}
        {textDone && !ended && <p className="battle-prompt">What will you do?</p>}
        {textDone && ended && <p className="battle-prompt">{pages[pages.length - 1]?.text}</p>}
        {battle.telegraph && !ended && textDone && <p className="battle-telegraph">{battle.telegraph}</p>}
      </div>
      {showMenu && (
        <ol className={`battle-menu battle-menu-${menu.page}`} aria-label={menu.page === 'root' ? 'Battle menu' : menu.page}>
          {items.map((item, index) => (
            <li key={item.id}>
              <button
                type="button"
                className={index === highlight ? 'battle-option battle-option-highlighted' : 'battle-option'}
                aria-current={index === highlight}
                disabled={item.disabled || busy}
                onMouseEnter={() => !item.disabled && setMenu((current) => ({ ...current, highlight: index }))}
                onClick={() => handle({ kind: 'pick', index }, false)}
              >
                <span className="battle-option-label">{item.label}</span>
                {item.detail && <span className="battle-option-detail">{item.detail}</span>}
              </button>
            </li>
          ))}
        </ol>
      )}
      {textDone && ended && (
        <button type="button" className="btn btn-primary battle-continue" onClick={onDone}>Continue</button>
      )}
    </section>
  );
}

