import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clauses, sentences, splitPages } from './pages';
import stormIntro from '../../../content/dialogue/storm_intro.json';
import corvinStorm from '../../../content/dialogue/corvin_storm.json';
import floodedRoad from '../../../content/dialogue/flooded_road.json';
import { dialogueAction, keyIntent } from './dialogueInput';
import type { DialogueMoment } from './dialogueInput';
import { isRead, markRead } from './readNodes';

/** A measure that fits at most `chars` characters, standing in for "two rendered lines". */
const fitsChars = (chars: number) => (text: string) => text.length <= chars;

describe('dialogue pages (P-44)', () => {
  it('fills each page with whole words up to what fits', () => {
    expect(splitPages('one two three four five', fitsChars(9))).toEqual(['one two', 'three', 'four five']);
  });

  it('keeps short text on one page', () => {
    expect(splitPages('Mind the ditch!', fitsChars(40))).toEqual(['Mind the ditch!']);
  });

  it('breaks at an explicit [page] marker, then fills and balances as usual', () => {
    expect(splitPages('First words. [page] Then more words here', fitsChars(15)))
      .toEqual(['First words.', 'Then more', 'words here']);
  });

  it('gives a word too long for any page a page of its own', () => {
    expect(splitPages('a extraordinarily b', fitsChars(5))).toEqual(['a', 'extraordinarily', 'b']);
  });

  it('never returns an empty list', () => {
    expect(splitPages('', fitsChars(5))).toEqual(['']);
  });
});

describe('dialogue keys (P-44)', () => {
  const moment = (over: Partial<DialogueMoment> = {}): DialogueMoment =>
    ({ pageDone: false, lastPage: false, alreadyRead: false, choices: [true, true, true], highlight: 0, ...over });

  it('maps keys to intents', () => {
    for (const key of ['e', 'E', ' ', 'Enter']) expect(keyIntent(key)).toEqual({ kind: 'primary' });
    for (const key of ['w', 'W', 'ArrowUp']) expect(keyIntent(key)).toEqual({ kind: 'up' });
    for (const key of ['s', 'S', 'ArrowDown']) expect(keyIntent(key)).toEqual({ kind: 'down' });
    expect(keyIntent('3')).toEqual({ kind: 'pick', index: 2 });
    expect(keyIntent('5')).toBeNull();
    expect(keyIntent('x')).toBeNull();
  });

  it('completes a typing page, then turns it', () => {
    expect(dialogueAction(moment(), { kind: 'primary' }, false)).toEqual({ kind: 'complete' });
    expect(dialogueAction(moment({ pageDone: true }), { kind: 'primary' }, false)).toEqual({ kind: 'next' });
  });

  it('takes the highlighted choice on the finished last page', () => {
    const last = moment({ pageDone: true, lastPage: true, highlight: 1 });
    expect(dialogueAction(last, { kind: 'primary' }, false)).toEqual({ kind: 'choose', index: 1 });
  });

  it('moves the highlight over unlocked choices, wrapping around', () => {
    const last = moment({ pageDone: true, lastPage: true, choices: [true, false, true], highlight: 0 });
    expect(dialogueAction(last, { kind: 'down' }, false)).toEqual({ kind: 'highlight', index: 2 });
    expect(dialogueAction({ ...last, highlight: 2 }, { kind: 'down' }, false)).toEqual({ kind: 'highlight', index: 0 });
    expect(dialogueAction(last, { kind: 'up' }, false)).toEqual({ kind: 'highlight', index: 2 });
  });

  it('takes a numbered choice directly, but not a locked or missing one', () => {
    const last = moment({ pageDone: true, lastPage: true, choices: [true, false] });
    expect(dialogueAction(last, { kind: 'pick', index: 0 }, false)).toEqual({ kind: 'choose', index: 0 });
    expect(dialogueAction(last, { kind: 'pick', index: 1 }, false)).toBeNull();
    expect(dialogueAction(last, { kind: 'pick', index: 3 }, false)).toBeNull();
    expect(dialogueAction(moment(), { kind: 'pick', index: 0 }, false), 'no choices before the last page').toBeNull();
  });

  it('fast-forwards a held key only through text already read, and never takes a choice', () => {
    expect(dialogueAction(moment(), { kind: 'primary' }, true), 'unread: a held key does nothing').toBeNull();
    expect(dialogueAction(moment({ alreadyRead: true }), { kind: 'primary' }, true)).toEqual({ kind: 'complete' });
    expect(dialogueAction(moment({ alreadyRead: true, pageDone: true }), { kind: 'primary' }, true)).toEqual({ kind: 'next' });
    const last = moment({ alreadyRead: true, pageDone: true, lastPage: true });
    expect(dialogueAction(last, { kind: 'primary' }, true)).toBeNull();
  });
});

describe('read dialogue nodes', () => {
  beforeEach(() => {
    const items = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => items.get(key) ?? null,
      setItem: (key: string, value: string) => void items.set(key, value),
      clear: () => items.clear(),
    });
  });

  it('remembers read nodes per profile', () => {
    expect(isRead(7, 'p01')).toBe(false);
    markRead(7, 'p01');
    expect(isRead(7, 'p01')).toBe(true);
    expect(isRead(8, 'p01')).toBe(false);
  });

  it('survives broken storage', () => {
    localStorage.setItem('shardbound.read.7', '{not json');
    expect(isRead(7, 'p01')).toBe(false);
    markRead(7, 'p01');
    expect(isRead(7, 'p01')).toBe(true);
  });
});

/**
 * Stand-ins for the rendered sizes: words wrap at `chars` per line; a page
 * holds at most 3 lines over the world (desktop, landscape) and 4 in the
 * portrait deck, the last page (which shares the space with the choices)
 * `lastLines`.
 */
const SIZES = {
  desktop: { chars: 92, lines: 3, lastLines: 2 },
  portrait: { chars: 36, lines: 4, lastLines: 3 },
  landscape: { chars: 64, lines: 3, lastLines: 3 },
};

function wrappedLines(text: string, chars: number): number {
  let lines = 1;
  let width = 0;
  for (const word of text.split(' ')) {
    if (width > 0 && width + 1 + word.length > chars) {
      lines++;
      width = word.length;
    } else {
      width += (width > 0 ? 1 : 0) + word.length;
    }
  }
  return lines;
}

/** [start, end) of each sentence in the text, in characters. */
function sentenceSpans(text: string): [number, number, string][] {
  let from = 0;
  return sentences(text).map((sentence) => {
    const start = text.indexOf(sentence, from);
    from = start + sentence.length;
    return [start, from, sentence];
  });
}

const TEXTS = {
  'Storm Night': stormIntro.nodes[0]!.text,
  Corvin: corvinStorm.nodes[0]!.text,
  'the flooded road': floodedRoad.nodes[0]!.text,
};

describe('dialogue pages at sentence boundaries', () => {
  it('splits text into sentences, keeping closing quotes with their sentence', () => {
    expect(sentences('"Mind the ditch!" Corvin shouts. "Go quick." Then silence'))
      .toEqual(['"Mind the ditch!"', 'Corvin shouts.', '"Go quick."', 'Then silence']);
  });

  it('splits a sentence into clauses after a comma, semicolon, colon or dash', () => {
    expect(clauses('The thunder stops, all at once; you see it: a thread of light — falling - slowly.'))
      .toEqual(['The thunder stops,', 'all at once;', 'you see it:', 'a thread of light —', 'falling -', 'slowly.']);
  });

  it('breaks a sentence too long for a page at a clause before a word', () => {
    expect(splitPages('The thunder stops, all at once, and the light falls.', fitsChars(36)))
      .toEqual(['The thunder stops, all at once,', 'and the light falls.']);
  });

  for (const [size, { chars, lines, lastLines }] of Object.entries(SIZES)) {
    const fits = (text: string) => wrappedLines(text, chars) <= lines;
    const fitsLast = (text: string) => wrappedLines(text, chars) <= lastLines;
    for (const [name, raw] of Object.entries(TEXTS)) {
      it(`pages ${name} at ${size} size: whole sentences, nothing lost, never a fragment last`, () => {
        const text = raw.replace(/\s+/g, ' ').trim();
        const pages = splitPages(text, fits, fitsLast);
        expect(pages.join(' ')).toBe(text);
        pages.slice(0, -1).forEach((page) => expect(fits(page), page).toBe(true));
        expect(fitsLast(pages.at(-1)!), pages.at(-1)).toBe(true);

        const spans = sentenceSpans(text);
        let offset = 0;
        pages.forEach((page, i) => {
          const start = offset;
          offset += page.length + 1;
          const inside = spans.find(([a, b]) => start > a && start < b);
          if (i === pages.length - 1) {
            expect(inside, `the final page starts inside a sentence: "${page}"`).toBeUndefined();
          } else if (inside && i > 0) {
            expect(fits(inside[2]), `a page breaks inside a sentence that fits a page: "${inside[2]}"`).toBe(false);
            // ...and after a clause mark, unless the clause itself is too long for a page.
            if (!/[,;:—–-]$/.test(pages[i - 1]!)) {
              const clause = clauses(inside[2]).find((c) => c.includes(pages[i - 1]!.split(' ').at(-1)!));
              expect(clause && !fits(clause), `a page breaks inside a clause that fits a page: "${clause}"`).toBe(true);
            }
          }
        });
      });
    }
  }
});

describe('left and right (P-61)', () => {
  it('reads A/D and the side arrows as left and right, which a dialogue ignores', () => {
    expect(keyIntent('a')).toEqual({ kind: 'left' });
    expect(keyIntent('ArrowRight')).toEqual({ kind: 'right' });
    const choosing = { pageDone: true, lastPage: true, alreadyRead: false, choices: [true, true], highlight: 0 };
    expect(dialogueAction(choosing, { kind: 'left' }, false)).toBeNull();
    expect(dialogueAction(choosing, { kind: 'right' }, false)).toBeNull();
  });
});
