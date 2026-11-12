import { Link } from 'react-router-dom';
import { Compass, Home } from 'lucide-react';
import { Button, Surface } from '../components/ui';

/**
 * 404.
 *
 * Rendered inside the shell rather than as a redirect. Silently sending
 * somebody to the dashboard hides the one thing they need to know — that the
 * link they followed was wrong — and leaves them wondering why they did not
 * arrive where they expected.
 */
export function NotFoundPage() {
  return (
    <div className="notfound">
      <Surface tone="raised" className="notfound__panel">
        <span className="notfound__glyph" aria-hidden="true">
          <Compass />
        </span>

        <p className="notfound__code">404</p>
        <h1 className="notfound__title">This page does not exist</h1>
        <p className="notfound__body">
          The link may be out of date, or the address mistyped. Nothing has happened to your money.
        </p>

        <div className="row justify-center gap-3">
          <Link to="/">
            <Button variant="primary" icon={<Home />}>
              Back to overview
            </Button>
          </Link>
          <Link to="/support">
            <Button variant="ghost">Contact support</Button>
          </Link>
        </div>
      </Surface>
    </div>
  );
}
