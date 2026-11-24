import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ToastBody } from './ToastLayer';
import type { ToastView } from '../api/types';

// A loot toast (P-65): the tier badge inline with the find's name, the name in the tier colour,
// and the way to the Bag when the server says the find beats the equipped weapon.
const hatchet: ToastView = {
  kind: 'LOOT', text: "Found: Woodcutter's Hatchet", subject: "Woodcutter's Hatchet", tier: 'E', upgrade: true,
};

describe('ToastBody', () => {
  it('puts the badge inline before the name, which carries the tier colour', () => {
    const html = renderToStaticMarkup(<ToastBody toast={hatchet} device="keyboard" />);
    expect(html).toMatch(/Found: <span class="tier-badge" data-tier="E"[^>]*>E<\/span> <span class="toast-subject" data-tier="E">Woodcutter&#x27;s Hatchet<\/span>/);
    expect(html).toContain('Press I to compare.');
  });

  it('names the Bag button on touch, and says nothing when the find is no upgrade', () => {
    expect(renderToStaticMarkup(<ToastBody toast={hatchet} device="touch" />)).toContain('Tap Bag to compare.');
    expect(renderToStaticMarkup(<ToastBody toast={{ ...hatchet, upgrade: false }} device="touch" />)).not.toContain('compare');
  });

  it('leaves other toasts as plain text', () => {
    const html = renderToStaticMarkup(<ToastBody toast={{ kind: 'SHARDS', text: 'Shards recovered', upgrade: false }} device="keyboard" />);
    expect(html).toBe('<span class="toast-kind">SHARDS</span><span>Shards recovered</span>');
  });
});
