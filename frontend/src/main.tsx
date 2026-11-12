import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';

// Inter as one variable font file, served from this app rather than from Google.
import '@fontsource-variable/inter/wght.css';

// Order matters: tokens define the variables every later file consumes.
import './styles/tokens.css';
import './styles/base.css';
import './styles/surfaces.css';
import './styles/components.css';
import './styles/cards.css';
import './styles/layout.css';
import './styles/pages.css';
import './styles/screens.css';
import './styles/home.css';
import './styles/transactions.css';
import './styles/send.css';
import './styles/admin.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element not found');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
