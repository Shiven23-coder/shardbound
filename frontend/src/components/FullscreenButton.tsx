import { enterFullscreen } from '../ui/fullscreen';

// Back to full screen after the player left it (P-53): a small square button
// beside Bag and Menu, at least 48 × 48 px to touch.
export default function FullscreenButton() {
  return (
    <button type="button" className="action-button fullscreen-button" aria-label="Full screen" onClick={enterFullscreen}>
      <span className="action-face" aria-hidden="true">
        <svg viewBox="0 0 16 16" width="16" height="16">
          <path fill="currentColor" d="M2 2h5v1.8H3.8V7H2zm7 0h5v5h-1.8V3.8H9zM2 9h1.8v3.2H7V14H2zm10.2 0H14v5H9v-1.8h3.2z" />
        </svg>
      </span>
    </button>
  );
}
