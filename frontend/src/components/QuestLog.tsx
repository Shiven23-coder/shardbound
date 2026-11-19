import type { QuestView } from '../api/types';

interface QuestLogProps {
  quests?: QuestView[];
}

// Quests in the order the server sent them (topological order in Java).
export default function QuestLog({ quests = [] }: QuestLogProps) {
  if (quests.length === 0) return <p className="empty">No quests yet.</p>;

  return (
    <ul className="quest-list">
      {quests.map((quest) => (
        <li key={quest.id} className="quest" data-status={quest.status}>
          <div className="quest-head">
            <strong>{quest.title}</strong>
            {quest.main && <span className="tag tag-main">Main</span>}
            <span className="tag quest-status">{quest.status}</span>
          </div>
          {quest.description && <p className="item-desc">{quest.description}</p>}
        </li>
      ))}
    </ul>
  );
}
