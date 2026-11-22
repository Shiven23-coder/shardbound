import { useEffect, useRef, useState } from 'react';

// The purse, bottom-right, shown for a moment whenever it changes (P-39).
// Gold until Shards replace it as the currency (V5).
const SHOW_MS = 2500;

interface CurrencyToastProps {
  amount: number;
  label: string;
}

export default function CurrencyToast({ amount, label }: CurrencyToastProps) {
  const previous = useRef(amount);
  const [delta, setDelta] = useState<number | null>(null);

  useEffect(() => {
    if (amount === previous.current) return undefined;
    setDelta(amount - previous.current);
    previous.current = amount;
    const timer = setTimeout(() => setDelta(null), SHOW_MS);
    return () => clearTimeout(timer);
  }, [amount]);

  if (delta === null) return null;
  return (
    <p className="currency-toast" role="status">
      <span aria-hidden="true">◈</span> {amount} {label}
      <span className="currency-delta">{delta > 0 ? `+${delta}` : delta}</span>
    </p>
  );
}
