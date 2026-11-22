import { useEffect, useState } from 'react';

// The area's name as a title card when the player arrives (P-47): large and
// centred for about 2 s. It is the first overlay on arrival (P-49): an
// opening dialogue waits for it. The screen keeps the name for assistive
// technology and automation in its data-area-name attribute.
export const TITLE_MS = 2000;

/** True while the title card of the area just entered is up. */
export function useAreaTitle(name: string): boolean {
  const [showing, setShowing] = useState(true);
  useEffect(() => {
    setShowing(true);
    const timer = setTimeout(() => setShowing(false), TITLE_MS);
    return () => clearTimeout(timer);
  }, [name]);
  return showing;
}

interface AreaTitleProps {
  name: string;
}

export default function AreaTitle({ name }: AreaTitleProps) {
  return (
    <p key={name} className="area-title" role="status">
      {name}
    </p>
  );
}
