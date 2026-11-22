// Dialogue nodes this profile has read to the end, so a held key may skip
// through them next time (P-44). Kept in this browser; the newest are kept.

const LIMIT = 500;
const key = (playerId: number) => `shardbound.read.${playerId}`;

function load(playerId: number): string[] {
  try {
    const stored = JSON.parse(localStorage.getItem(key(playerId)) ?? '[]');
    return Array.isArray(stored) ? stored.filter((id): id is string => typeof id === 'string') : [];
  } catch {
    return [];
  }
}

export function isRead(playerId: number, nodeId: string): boolean {
  return load(playerId).includes(nodeId);
}

export function markRead(playerId: number, nodeId: string): void {
  const read = load(playerId).filter((id) => id !== nodeId);
  read.push(nodeId);
  try {
    localStorage.setItem(key(playerId), JSON.stringify(read.slice(-LIMIT)));
  } catch {
    // Storage unavailable: the node just counts as unread next time.
  }
}
