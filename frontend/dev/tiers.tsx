// Dev only (P-65): every weapon in content/world.json as a card, rated by the
// server for a new player (GET /api/debug/weapons, a dev-profile route). Served
// by `npm run dev` at /dev/tiers.html; not a build input, so never shipped.
import { createRoot } from 'react-dom/client';
import WeaponCard from '../src/components/WeaponCard';
import type { WeaponView } from '../src/api/types';
import '../src/styles/theme.css';
import '../src/styles/regions.css';
import '../src/styles/components.css';
import '../src/styles/tiers.css';
import '../src/styles/animations.css';

const root = createRoot(document.getElementById('root')!);
const response = await fetch('/api/debug/weapons');
if (response.ok) {
  const ladder = (await response.json()) as WeaponView[];
  root.render(
    <main className="tier-gallery">
      {ladder.map((weapon) => <WeaponCard key={weapon.id} weapon={weapon} />)}
    </main>,
  );
} else {
  root.render(<p>The gallery needs the API in its dev profile: run `npm run dev` from the repository root.</p>);
}
