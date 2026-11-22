import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { installAudio } from './audio/audio';
import './styles/theme.css';
import './styles/regions.css';
import './styles/components.css';
import './styles/tiers.css';
import './styles/animations.css';

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('index.html has no #root element');

installAudio();

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
