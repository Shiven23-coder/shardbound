import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import Typewriter from './Typewriter';
import ChoiceList from './ChoiceList';
import { actorArt, facesetUrl } from '../world/content';
import { useGamepadIntents } from './gamepadChoices';
import { audio, audioTiming } from '../audio/audio';
import { useGame } from '../state/GameContext';
import { splitPages } from '../dialogue/pages';
import { dialogueAction, firstOpen, keyIntent } from '../dialogue/dialogueInput';
import type { DialogueAction, Intent } from '../dialogue/dialogueInput';
import { isRead, markRead } from '../dialogue/readNodes';
import type { DialogueView } from '../api/types';

// A dialogue: the speaker's portrait (from the art manifest), the title, the
// text in pages, and the choices exactly as the server sent them (P-44). It
// lies over the bottom of the world, or fills the control deck in phone
// portrait (P-40). Pages hold at most 3 lines over the world and 4 in the deck,
// fewer if the box has no room (the last one leaves room for the choices), and
// break between sentences, else clauses, else words (P-50). The box fits its
// pages, and every page but the last keeps the height of the tallest one. Each page types out; E,
// Space, Enter, gamepad A or a tap completes it, then turns it. Choices come on
// the last page, with a highlight for W/S, the arrows and the gamepad, and
// number keys 1–4. A node whose only option just closes it is a message box:
// no choice button, closed with E, A or a tap (P-49).
/** Lines per page at most: over the world, and in the portrait deck (P-50). */
const MAX_LINES = { overlay: 3, deck: 4 } as const;

interface DialogueBoxProps {
  dialogue: DialogueView;
  onChoose: (choiceId: number) => void;
  busy: boolean;
  placement: 'overlay' | 'deck';
}

export default function DialogueBox({ dialogue, onChoose, busy, placement }: DialogueBoxProps) {
  const playerId = useGame().state.player?.playerId;
  const faceset = dialogue.speaker ? actorArt(`npc.${dialogue.speaker}`)?.faceset : undefined;
  const text = dialogue.text ?? '';
  const choices = useMemo(() => dialogue.choices ?? [], [dialogue.choices]);
  const open = useMemo(() => choices.map((c) => !c.locked), [choices]);
  const message = dialogue.message;

  const [pages, setPages] = useState<string[]>([text]);
  /** The tallest page, in lines: every page but the last keeps that height, so the box does not jump. */
  const [pageLines, setPageLines] = useState(0);
  const [page, setPage] = useState(0);
  const [pageDone, setPageDone] = useState(false);
  const [highlight, setHighlight] = useState(() => firstOpen(open));
  const alreadyRead = useMemo(() => playerId !== undefined && isRead(playerId, dialogue.nodeId), [playerId, dialogue.nodeId]);
  const measure = useRef<HTMLDivElement>(null);
  const body = useRef<HTMLDivElement>(null);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const choicesMeasure = useRef<HTMLDivElement>(null);
  const lastPage = page >= pages.length - 1;

  // A new node starts on its first page with the first open choice highlighted.
  const openNow = useRef(open);
  openNow.current = open;
  useEffect(() => {
    setPage(0);
    setPageDone(false);
    setHighlight(firstOpen(openNow.current));
  }, [dialogue.nodeId]);

  // Pages measured in the real font: up to MAX_LINES, fewer if the box's share of the screen has
  // no room, the last page fewer when the choices sit below the text. Again on any resize.
  useLayoutEffect(() => {
    const element = measure.current;
    const box = body.current;
    const heading = titleRef.current;
    if (!element || !box || !heading) return undefined;
    let measured = '';
    const paginate = () => {
      const lineHeight = parseFloat(getComputedStyle(element).lineHeight);
      // The box grows with its text up to its share (--dialogue-share) of the screen.
      const section = box.parentElement!;
      const share = parseFloat(getComputedStyle(section).getPropertyValue('--dialogue-share'));
      const outer = (section.offsetParent as HTMLElement).clientHeight * share;
      const chrome = section.clientHeight - box.clientHeight;          // the box's own padding
      const space = outer - chrome - heading.offsetHeight - parseFloat(getComputedStyle(heading).marginBottom)
        - parseFloat(getComputedStyle(element.parentElement!).marginBottom);
      const beside = getComputedStyle(box).getPropertyValue('--choices-beside').trim() === '1';
      const choiceSpace = message || beside ? 0 : choicesMeasure.current?.offsetHeight ?? 0;
      const lines = Math.min(MAX_LINES[placement], Math.max(1, Math.floor(space / lineHeight)));
      const lastLines = Math.min(lines, Math.max(1, Math.floor((space - choiceSpace) / lineHeight)));
      const key = `${element.clientWidth}:${lines}:${lastLines}`;
      if (key === measured) return;
      measured = key;
      const fitsIn = (count: number) => (candidate: string) => {
        element.textContent = candidate;
        return element.scrollHeight <= count * lineHeight + 1;
      };
      const next = splitPages(text, fitsIn(lines), fitsIn(lastLines));
      setPages(next);
      setPageLines(Math.max(0, ...next.slice(0, -1).map((candidate) => {
        element.textContent = candidate;
        return Math.round(element.scrollHeight / lineHeight);
      })));
      setPage((current) => Math.min(current, next.length - 1));
    };
    paginate();
    const observer = new ResizeObserver(paginate);
    observer.observe(box);
    observer.observe(element);
    return () => observer.disconnect();
  }, [text, message, choices, placement]);

  useEffect(() => {
    if (pageDone && lastPage && playerId !== undefined) markRead(playerId, dialogue.nodeId);
  }, [pageDone, lastPage, playerId, dialogue.nodeId]);

  const act = (action: DialogueAction | null) => {
    if (!action) return;
    if (action.kind === 'complete') setPageDone(true);
    if (action.kind === 'next') {
      setPage((current) => current + 1);
      setPageDone(false);
    }
    if (action.kind === 'highlight') setHighlight(action.index);
    if (action.kind === 'choose' && !busy) onChoose(choices[action.index]!.id);
  };
  const handle = (intent: Intent, held: boolean) =>
    act(dialogueAction({ pageDone, lastPage, alreadyRead, choices: open, highlight }, intent, held));
  const latest = useRef(handle);
  latest.current = handle;

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey || inTextField(event.target)) return;
      if (document.querySelector('[aria-modal="true"]')) return;   // a menu panel is on top
      const intent = keyIntent(event.key);
      if (!intent || intent.kind === 'back') return;      // Esc opens the menu, as everywhere
      event.preventDefault();    // no native button click or page scroll, and the world scene ignores it
      latest.current(intent, event.repeat);
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, []);
  useGamepadIntents(useCallback((intent: Intent, held: boolean) => latest.current(intent, held), []));

  const lastBlip = useRef(0);
  const blip = useCallback((shown: number) => {
    if (shown < lastBlip.current) lastBlip.current = 0; // a new page
    if (shown - lastBlip.current < audioTiming.blipEveryChars) return;
    lastBlip.current = shown;
    audio.play('blip');
  }, []);

  return (
    <section
      className={`dialogue-box dialogue-${placement} panel${message ? ' dialogue-message' : ''}`}
      aria-labelledby="dialogue-title"
    >
      {faceset && <img className="dialogue-portrait" src={facesetUrl(faceset)} alt="" />}
      <div ref={body} className="dialogue-body">
        <h2 ref={titleRef} id="dialogue-title" className="dialogue-title">{dialogue.title}</h2>
        <div
          className="dialogue-page"
          style={{ '--page-lines': lastPage ? 0 : pageLines } as CSSProperties}
          // A tap on the text never takes a real choice; it does close a message.
          onClick={() => (message || !(pageDone && lastPage)) && handle({ kind: 'primary' }, false)}
        >
          <Typewriter
            key={`${dialogue.nodeId}:${page}:${pages.length}`}
            text={pages[page]}
            className="dialogue-text"
            complete={pageDone}
            onProgress={blip}
            onDone={() => setPageDone(true)}
          />
          <div ref={measure} className="dialogue-text dialogue-measure" aria-hidden="true" />
        </div>
        {pageDone && !lastPage && (
          <button type="button" className="dialogue-more" aria-label="Continue" onClick={() => handle({ kind: 'primary' }, false)}>
            ▼
          </button>
        )}
        {pageDone && lastPage && message && (
          <button
            type="button"
            className="dialogue-more dialogue-close"
            aria-label={choices[0]!.label}
            title={choices[0]!.label}
            disabled={busy}
            onClick={() => handle({ kind: 'primary' }, false)}
          >
            ■
          </button>
        )}
        {pageDone && lastPage && !message && (
          <ChoiceList choices={choices} onChoose={onChoose} disabled={busy} highlight={highlight} onHighlight={setHighlight} />
        )}
        {!message && (
          // The choices' height, for the last page's measure; never seen or reached.
          <div ref={choicesMeasure} className="dialogue-choices-measure" aria-hidden="true" inert>
            <ChoiceList choices={choices} onChoose={() => undefined} disabled highlight={-1} onHighlight={() => undefined} />
          </div>
        )}
      </div>
    </section>
  );
}

function inTextField(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
}
