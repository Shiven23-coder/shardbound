import { useEffect, useState } from 'react';

// The current objective (P-46), chosen by the server. When it changes (and
// when the world first opens) it waits its turn (P-49): it shows for a few
// seconds once no title card or dialogue is up. The pause menu always shows it.
const SHOW_MS = 5000;

/** The objective line to show now, and whether one is waiting for its turn. */
export function useObjectiveBanner(objective: string | null | undefined, mayShow: boolean) {
  const [waiting, setWaiting] = useState<string | null>(null);
  const [shown, setShown] = useState<string | null>(null);

  useEffect(() => {
    if (objective) setWaiting(objective);
  }, [objective]);

  useEffect(() => {
    if (!mayShow) {
      setShown(null);    // something with priority came up: this one has had its moment
      return undefined;
    }
    if (!waiting) return undefined;
    setShown(waiting);
    setWaiting(null);
    return undefined;
  }, [mayShow, waiting]);

  useEffect(() => {
    if (!shown) return undefined;
    const timer = setTimeout(() => setShown(null), SHOW_MS);
    return () => clearTimeout(timer);
  }, [shown]);

  return { shown, waiting: waiting !== null || shown !== null };
}

interface ObjectiveLineProps {
  objective: string;
}

export default function ObjectiveLine({ objective }: ObjectiveLineProps) {
  return (
    <p key={objective} className="objective-line" role="status">
      <span className="objective-label">Objective</span> {objective}
    </p>
  );
}
