import { AnimatePresence, motion } from 'framer-motion';
import Typewriter from './Typewriter';
import ChoiceList from './ChoiceList';
import type { GameView } from '../api/types';

// The centre of the game screen: where we are, the node text, and the choices.
interface StoryPanelProps {
  view: GameView;
  onChoose: (choiceId: number) => void;
  busy: boolean;
}

export default function StoryPanel({ view, onChoose, busy }: StoryPanelProps) {
  const { node, choices, locationName, chapter } = view;

  return (
    <section className="story-panel panel" aria-labelledby="node-title">
      <p className="story-place">
        Chapter {chapter} · {locationName}
      </p>
      <AnimatePresence mode="wait">
        <motion.div
          key={node.id}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.25 }}
        >
          <h2 id="node-title" className="story-title">{node.title}</h2>
          <Typewriter key={node.id} text={node.text} className="story-text" />
        </motion.div>
      </AnimatePresence>
      <ChoiceList choices={choices} onChoose={onChoose} disabled={busy} />
    </section>
  );
}
