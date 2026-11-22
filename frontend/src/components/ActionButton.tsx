import { forwardRef } from 'react';

// Bag and Menu (P-47): small icon buttons with their key on desktop (I, Esc),
// small pills on phones, still at least 48 × 48 px to touch.
export type ActionButtonVariant = 'icon' | 'pill';

interface ActionButtonProps {
  label: string;
  icon: 'bag' | 'menu';
  keyHint: string;
  variant: ActionButtonVariant;
  expanded: boolean;
  onClick: () => void;
}

const ActionButton = forwardRef<HTMLButtonElement, ActionButtonProps>(function ActionButton(
  { label, icon, keyHint, variant, expanded, onClick }, ref,
) {
  return (
    <button
      ref={ref}
      type="button"
      className={`action-button action-${variant}`}
      aria-label={label}
      aria-expanded={expanded}
      title={variant === 'icon' ? `${label} (${keyHint})` : undefined}
      onClick={onClick}
    >
      <span className="action-face" aria-hidden="true">
        {variant === 'icon' ? (
          <>
            {icon === 'bag' ? <BagIcon /> : <MenuIcon />}
            <kbd className="action-key">{keyHint}</kbd>
          </>
        ) : label}
      </span>
    </button>
  );
});

export default ActionButton;

function BagIcon() {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16">
      <path fill="currentColor" d="M5 5V4a3 3 0 0 1 6 0v1h2l1 10H2L3 5h2zm1.5 0h3V4a1.5 1.5 0 0 0-3 0v1z" />
    </svg>
  );
}

function MenuIcon() {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16">
      <path fill="currentColor" d="M2 3.5h12v1.8H2zm0 3.6h12v1.8H2zm0 3.6h12v1.8H2z" />
    </svg>
  );
}
