// The player's recent path, newest first (as sent by the server).
export default function Journal({ journal = [] }) {
  if (journal.length === 0) return <p className="empty">Your journal is empty.</p>;

  return (
    <ol className="journal">
      {journal.map((entry, i) => (
        <li key={i} className="journal-entry">
          <span className="journal-node">{entry.nodeTitle}</span>
          {entry.choiceLabel && <span className="journal-choice">→ {entry.choiceLabel}</span>}
        </li>
      ))}
    </ol>
  );
}
