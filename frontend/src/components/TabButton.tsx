// One tab header button in the SaveLoadModal (Save / Load).

interface TabButtonProps {
  label: string;
  active: boolean;
  disabled?: boolean;
  onClick: () => void;
}

export default function TabButton({ label, active, disabled, onClick }: TabButtonProps) {
  return (
    <button type="button" role="tab" aria-selected={active} className={active ? 'tab tab-active' : 'tab'} disabled={disabled} onClick={onClick}>
      {label}
    </button>
  );
}
