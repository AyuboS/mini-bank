import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';

// Inter as one variable font file, served from this app rather than from Google.
import '@fontsource-variable/inter/wght.css';

// Order matters: tokens define the variables every later file consumes.
import './styles/tokens.css';
import './styles/base.css';
import './styles/surfaces.css';
import './styles/buttons.css';
import './styles/fields-and-sheets.css';
import './styles/indicators.css';
import './styles/feedback.css';
import './styles/components-responsive.css';
import './styles/cards.css';
import './styles/layout.css';
import './styles/page-basics.css';
import './styles/analytics.css';
import './styles/payments-pages.css';
import './styles/palette-and-accounts.css';
import './styles/sign-in.css';
import './styles/pages-responsive.css';
import './styles/screens.css';
import './styles/screens-extra.css';
import './styles/home.css';
import './styles/transactions.css';
import './styles/send.css';
import './styles/admin.css';
import './styles/statements.css';
import './styles/savings.css';
import './styles/profile.css';
import './styles/utilities.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root element not found');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
