// Splits dialogue text into pages (P-44, P-50). Pages are filled with whole
// sentences while they fit. A sentence too long for a page is broken between
// clauses (after a comma, semicolon, colon or dash), and only a clause too
// long for a page is broken between words. The last page, which shares its
// space with the choices, gets its own measure, and the last two pages are
// rebalanced so the final one starts a sentence wherever possible and the two
// are even. A `[page]` marker always starts a new page. `fits` measures in the
// real font and size; tests pass a stand-in.

export const PAGE_BREAK = /\s*\[page\]\s*/;

/** End of a sentence: . ! ? or … with any closing quotes or brackets, then a space or the end. */
const SENTENCE = /[^.!?…]*[.!?…]+["'”’)\]]*(?=\s|$)|[^.!?…]+$/g;
/** End of a clause: , ; : or a dash (—, –, or - between spaces), then a space or the end. */
const CLAUSE = /.*?(?:[,;:—–]|\s-)(?=\s|$)|.+$/g;
const ENDS_SENTENCE = /[.!?…]["'”’)\]]*$/;
const ENDS_CLAUSE = /(?:[,;:—–]|\s-)$/;

/** The sentences of a text, each with its closing punctuation. */
export function sentences(text: string): string[] {
  return (text.match(SENTENCE) ?? []).map((s) => s.trim()).filter((s) => s !== '');
}

/** The clauses of a sentence, each with the mark that ends it. */
export function clauses(sentence: string): string[] {
  return (sentence.match(CLAUSE) ?? []).map((c) => c.trim()).filter((c) => c !== '');
}

type Fits = (candidate: string) => boolean;

export function splitPages(text: string, fits: Fits, fitsLast: Fits = fits): string[] {
  const segments = text.split(PAGE_BREAK).map((s) => s.replace(/\s+/g, ' ').trim()).filter((s) => s !== '');
  if (segments.length === 0) return [''];
  // Only the final [page] segment shares the last page with the choices, so only it is rebalanced.
  const earlier = segments.slice(0, -1).flatMap((segment) => fill(segment, fits));
  return [...earlier, ...finish(fill(segments.at(-1)!, fits), fits, fitsLast)];
}

/** Packs units (sentences, clauses, words) into pages while they fit; a unit too long for a page is split by `smaller`. */
function pack(units: string[], fits: Fits, smaller: (unit: string) => string[]): string[] {
  const pages: string[] = [];
  let page = '';
  for (const unit of units) {
    const candidate = page ? `${page} ${unit}` : unit;
    if (fits(candidate)) {
      page = candidate;
      continue;
    }
    if (page) pages.push(page);
    if (fits(unit)) {
      page = unit;
    } else {
      const pieces = smaller(unit);
      pages.push(...pieces.slice(0, -1));
      page = pieces.at(-1)!;
    }
  }
  if (page) pages.push(page);
  return pages;
}

/** Whole sentences; a sentence too long breaks between clauses; a clause too long breaks between words. */
function fill(segment: string, fits: Fits): string[] {
  const byWords = (text: string) => pack(text.split(' '), fits, (word) => [word]);   // a word too long gets its own page
  const byClauses = (sentence: string) => pack(clauses(sentence), fits, byWords);
  return pack(sentences(segment), fits, byClauses);
}

/**
 * Makes the last page fit beside the choices, then rebalances the last two
 * pages: the final page starts a sentence wherever a split allows it (else a
 * clause, else a word), and the two are as even as possible, so the choices
 * never sit under a scrap of text.
 */
function finish(pages: string[], fits: Fits, fitsLast: Fits): string[] {
  const result = [...pages];
  while (!fitsLast(result.at(-1)!)) {
    const [rest, last] = splitForLast(result.at(-1)!, fitsLast);
    if (!rest) break;   // not even one word fits beside the choices: keep it as it is
    result.splice(-1, 1, rest, last);
  }
  if (result.length < 2) return result;
  const words = `${result.at(-2)!} ${result.at(-1)!}`.split(' ');
  let best: [string, string] | null = null;
  let bestScore = Infinity;
  for (let cut = 1; cut < words.length; cut++) {
    const first = words.slice(0, cut).join(' ');
    const second = words.slice(cut).join(' ');
    if (!fits(first) || !fitsLast(second)) continue;
    const boundary = ENDS_SENTENCE.test(first) ? 0 : ENDS_CLAUSE.test(first) ? 1 : 2;
    const score = boundary * 1_000_000 + Math.abs(first.length - second.length);
    if (score < bestScore) {
      bestScore = score;
      best = [first, second];
    }
  }
  if (best) result.splice(-2, 2, ...best);
  return result;
}

/** The longest ending that fits beside the choices: whole sentences, else clauses, else words. */
function splitForLast(page: string, fitsLast: Fits): [string, string] {
  const words = page.split(' ');
  const cuts = [ENDS_SENTENCE, ENDS_CLAUSE, /$/];
  for (const boundary of cuts) {
    for (let from = 1; from < words.length; from++) {
      const before = words.slice(0, from).join(' ');
      const last = words.slice(from).join(' ');
      if (boundary.test(before) && fitsLast(last)) return [before, last];
    }
  }
  return ['', page];
}
