import { useEffect, useState } from 'react';

// The boss's name across the arena as each attempt begins, and FOE VANQUISHED
// when it falls (P-67). Each shows once per key and fades by itself.
/** How long a banner stays: its CSS animation lasts as long. */
const BANNER_MS = 2600;

interface BossBannerProps {
  /** The banner's text, or null for none. */
  text: string | null;
  kind: 'name' | 'vanquished';
}

export default function BossBanner({ text, kind }: BossBannerProps) {
  const [shown, setShown] = useState<string | null>(null);
  useEffect(() => {
    if (!text) return undefined;
    setShown(text);
    const timer = setTimeout(() => setShown(null), BANNER_MS);
    return () => clearTimeout(timer);
  }, [text]);
  if (!shown) return null;
  return <p className={`boss-banner boss-banner-${kind}`} role="status">{shown}</p>;
}
