import { describe, expect, it, vi } from 'vitest';
import { Channel, WorldBridge } from './bridge';
import { makeView } from '../testing/fixtures';

describe('Channel', () => {
  it('delivers each event to its own listeners only', () => {
    const channel = new Channel<{ a: number; b: string }>();
    const onA = vi.fn();
    const onB = vi.fn();
    channel.on('a', onA);
    channel.on('b', onB);
    channel.emit('a', 1);
    expect(onA).toHaveBeenCalledWith(1);
    expect(onB).not.toHaveBeenCalled();
  });

  it('stops delivering after unsubscribing', () => {
    const channel = new Channel<{ a: number }>();
    const listener = vi.fn();
    const off = channel.on('a', listener);
    off();
    channel.emit('a', 1);
    expect(listener).not.toHaveBeenCalled();
  });
});

describe('WorldBridge', () => {
  it('remembers the latest view for a scene that starts later', () => {
    const bridge = new WorldBridge();
    const view = makeView().world;
    bridge.sendView(view);
    expect(bridge.latestView).toBe(view);
  });

  it('tells the scene when a dialogue opens, changes its cover or closes, once per change', () => {
    const bridge = new WorldBridge();
    const listener = vi.fn();
    bridge.toScene.on('dialogue', listener);
    bridge.setDialogue(1 / 3);
    bridge.setDialogue(1 / 3);
    bridge.setDialogue(0);
    bridge.setDialogue(null);
    expect(listener.mock.calls).toEqual([[1 / 3], [0], [null]]);
    expect(bridge.dialogueCover).toBeNull();
  });

  it('starts frozen and tells the scene when walking resumes', () => {
    const bridge = new WorldBridge();
    const listener = vi.fn();
    bridge.toScene.on('frozen', listener);
    expect(bridge.frozen).toBe(true);
    bridge.setFrozen(false);
    expect(listener).toHaveBeenCalledWith(false);
    expect(bridge.frozen).toBe(false);
  });
});
