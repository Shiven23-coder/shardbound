import { afterEach, describe, expect, it, vi } from 'vitest';
import { initialState, readStoredPlayer, reducer, storePlayer } from './gameReducer';
import type { State } from './gameReducer';
import { MemoryStorage, blockedStorage, makeView } from '../testing/fixtures';

// Start every test from the same state, whatever the environment's storage holds.
const base: State = { ...initialState, player: null };

describe('reducer', () => {
  it('stores the received GameView verbatim and switches to the game screen', () => {
    const view = makeView();
    const next = reducer(base, { type: 'SET_VIEW', view });
    expect(next.view).toBe(view); // same object, not a copy
    expect(next.viewSeq).toBe(base.viewSeq + 1);
    expect(next.screen).toBe('game');
  });

  it('bumps viewSeq on every new view and leaves the old state untouched', () => {
    const first = reducer(base, { type: 'SET_VIEW', view: makeView() });
    const second = reducer(first, { type: 'SET_VIEW', view: makeView({ chapter: 2 }) });
    expect(second.viewSeq).toBe(2);
    expect(first.viewSeq).toBe(1);
    expect(base.view).toBeNull();
  });

  it('GO remembers the previous screen, or the explicit returnTo', () => {
    const inGame = { ...base, screen: 'game' as const };
    expect(reducer(inGame, { type: 'GO', screen: 'achievements' })).toMatchObject({
      screen: 'achievements',
      returnScreen: 'game',
    });
    expect(reducer(inGame, { type: 'GO', screen: 'achievements', returnTo: 'title' }).returnScreen).toBe('title');
  });

  it('GO_PROFILE opens the profile screen with the follow-up step', () => {
    const next = reducer(base, { type: 'GO_PROFILE', next: 'load' });
    expect(next.screen).toBe('profile');
    expect(next.profileNext).toBe('load');
  });

  it('QUIT clears the view and modal and returns to the title', () => {
    const playing = reducer({ ...base, modal: 'save' }, { type: 'SET_VIEW', view: makeView() });
    const next = reducer(playing, { type: 'QUIT' });
    expect(next).toMatchObject({ view: null, modal: null, screen: 'title' });
  });

  it('SET_ERROR keeps the message and counts up seq so repeats still show', () => {
    const once = reducer(base, { type: 'SET_ERROR', message: 'Not enough energy' });
    const twice = reducer(once, { type: 'SET_ERROR', message: 'Not enough energy' });
    expect(once.error).toEqual({ message: 'Not enough energy', seq: 1 });
    expect(twice.error?.seq).toBe(2);
  });

  it('sets the busy and demo flags', () => {
    expect(reducer(base, { type: 'SET_BUSY', busy: true }).busy).toBe(true);
    expect(reducer(base, { type: 'SET_DEMO', demoMode: true }).demoMode).toBe(true);
  });

  it('toggles the inspector and opens/closes the save-load modal', () => {
    const open = reducer(base, { type: 'TOGGLE_INSPECTOR' });
    expect(open.inspectorOpen).toBe(true);
    expect(reducer(open, { type: 'TOGGLE_INSPECTOR' }).inspectorOpen).toBe(false);

    const modal = reducer(base, { type: 'OPEN_MODAL', tab: 'load' });
    expect(modal.modal).toBe('load');
    expect(reducer(modal, { type: 'CLOSE_MODAL' }).modal).toBeNull();
  });

  it('SET_PLAYER stores the chosen profile', () => {
    const player = { playerId: 7, username: 'mira' };
    expect(reducer(base, { type: 'SET_PLAYER', player }).player).toEqual(player);
  });
});

describe('stored player', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('round-trips a player through sessionStorage', () => {
    vi.stubGlobal('sessionStorage', new MemoryStorage());
    storePlayer({ playerId: 3, username: 'ash' });
    expect(readStoredPlayer()).toEqual({ playerId: 3, username: 'ash' });
  });

  it('returns null for missing, malformed or blocked storage', () => {
    const storage = new MemoryStorage();
    vi.stubGlobal('sessionStorage', storage);
    expect(readStoredPlayer()).toBeNull();
    storage.setItem('shardbound.player', '{not json');
    expect(readStoredPlayer()).toBeNull();

    vi.stubGlobal('sessionStorage', blockedStorage);
    expect(readStoredPlayer()).toBeNull();
    expect(() => storePlayer(null)).not.toThrow();
  });
});
