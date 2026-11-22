import Modal from './Modal';

// The Controls screen (P-41), from the title screen and the pause menu.
interface ControlsPanelProps {
  onClose: () => void;
}

const SECTIONS: { title: string; rows: [string, string][] }[] = [
  {
    title: 'Keyboard',
    rows: [['WASD or arrow keys', 'Walk'], ['E or Space', 'Talk, read, use'], ['I', 'Bag'], ['Esc', 'Menu'], ['Enter or click', 'Pick a choice']],
  },
  {
    title: 'Gamepad',
    rows: [['Left stick or D-pad', 'Walk'], ['A', 'Talk, read, use; pick the highlighted choice'], ['D-pad up / down', 'Move between choices']],
  },
  {
    title: 'Touch',
    rows: [['Arrows (hold)', 'Walk'], ['Ⓐ button', 'Talk, read, use'], ['Tap a person or sign next to you', 'Talk or read'],
      ['Tap the HP bars', 'Show the numbers'], ['Bag, Menu', 'Inventory and quests; save, settings, controls']],
  },
];

export default function ControlsPanel({ onClose }: ControlsPanelProps) {
  return (
    <Modal title="Controls" onClose={onClose}>
      {SECTIONS.map((section) => (
        <section key={section.title} className="controls-section">
          <h3>{section.title}</h3>
          <dl className="controls-list">
            {section.rows.map(([input, action]) => (
              <div key={input}>
                <dt>{input}</dt>
                <dd>{action}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
    </Modal>
  );
}
