// The battle menu (V2_SPEC §4.7, P-57): ATTACK (Strike / Guard), SKILLS (the
// four slots), BAG and FLEE, with the same keys, gamepad buttons and taps as
// dialogue. Pure, so every device shares one set of rules.
import type { BattleRequest, MenuView } from '../api/types';
import type { Intent } from '../dialogue/dialogueInput';

export type MenuPage = 'root' | 'attack' | 'skills' | 'bag';

/** One entry: it opens a page, sends an action, or goes back. */
export interface MenuItem {
  id: string;
  label: string;
  detail?: string;
  disabled: boolean;
  page?: MenuPage;
  action?: BattleRequest;
  back?: boolean;
}

export interface MenuState {
  page: MenuPage;
  highlight: number;
}

export const ROOT: MenuState = { page: 'root', highlight: 0 };

const BACK: MenuItem = { id: 'back', label: 'Back', disabled: false, back: true };

/** The entries of a page, from the server's menu (what is usable is decided there). */
export function menuItems(page: MenuPage, menu: MenuView): MenuItem[] {
  switch (page) {
    case 'root':
      return [
        { id: 'attack', label: 'Attack', disabled: false, page: 'attack' },
        { id: 'skills', label: 'Skills', disabled: !menu.skills.some((s) => s.id), page: 'skills' },
        { id: 'bag', label: 'Bag', disabled: menu.bag.length === 0, page: 'bag' },
        { id: 'flee', label: 'Flee', disabled: !menu.canFlee, action: { kind: 'FLEE', slot: 0 } },
      ];
    case 'attack':
      return [
        { id: 'strike', label: 'Strike', detail: 'A plain attack', disabled: false, action: { kind: 'STRIKE', slot: 0 } },
        { id: 'guard', label: 'Guard', detail: 'Halve the next hit', disabled: false, action: { kind: 'GUARD', slot: 0 } },
        BACK,
      ];
    case 'skills':
      return [
        ...menu.skills.map((s): MenuItem => (s.id
          ? {
            id: `skill-${s.slot}`, label: s.name ?? s.id, detail: `${s.fpCost} FP · ${titleCase(s.element ?? '')}`,
            disabled: !s.usable, action: { kind: 'SKILL', slot: s.slot },
          }
          : { id: `skill-${s.slot}`, label: '—', disabled: true })),
        BACK,
      ];
    case 'bag':
      return [
        ...menu.bag.map((item): MenuItem => ({
          // A flask (P-63) shows its charges, "2/3", and cannot be drunk empty.
          id: `item-${item.id}`, label: item.maxQty ? `${item.name} ${item.qty}/${item.maxQty}` : `${item.name} ×${item.qty}`,
          detail: itemDetail(item.heal, item.fp), disabled: item.qty === 0, action: { kind: 'ITEM', slot: 0, itemId: item.id },
        })),
        BACK,
      ];
  }
}

/** What an intent does: a new menu state, and an action to send when one was taken. */
export function menuStep(state: MenuState, menu: MenuView, intent: Intent): { state: MenuState; action?: BattleRequest } {
  const items = menuItems(state.page, menu);
  if (intent.kind === 'left' || intent.kind === 'right') return { state };     // the level-up screen's − and +
  if (intent.kind === 'back') return { state: state.page === 'root' ? state : ROOT };
  if (intent.kind === 'up' || intent.kind === 'down') {
    return { state: { ...state, highlight: nextEnabled(items, state.highlight, intent.kind === 'up' ? -1 : 1) } };
  }
  const index = intent.kind === 'pick' ? intent.index : state.highlight;
  const item = items[index];
  if (!item || item.disabled) return { state };
  if (item.back) return { state: ROOT };
  if (item.page) return { state: { page: item.page, highlight: firstEnabled(menuItems(item.page, menu)) } };
  return { state: ROOT, action: item.action };
}

/** Index of the first enabled entry (0 when none is). */
export function firstEnabled(items: MenuItem[]): number {
  return Math.max(0, items.findIndex((item) => !item.disabled));
}

function nextEnabled(items: MenuItem[], from: number, step: number): number {
  for (let i = 1; i <= items.length; i++) {
    const index = (((from + step * i) % items.length) + items.length) % items.length;
    if (!items[index]!.disabled) return index;
  }
  return from;
}

function itemDetail(heal: number, fp: number): string {
  return [heal > 0 ? `+${heal} HP` : '', fp > 0 ? `+${fp} FP` : ''].filter(Boolean).join(' · ');
}

function titleCase(word: string): string {
  return word.charAt(0) + word.slice(1).toLowerCase();
}
