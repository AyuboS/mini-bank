/** A public demo build, made with VITE_DEMO_MODE=true (as on Vercel). */
export const PUBLIC_DEMO = import.meta.env.VITE_DEMO_MODE === 'true';

/** The demo sign-in shortcuts: shown in development and in a public demo build. */
export const SHOW_DEMO_ACCOUNTS = import.meta.env.DEV || PUBLIC_DEMO;

/**
 * Whether steps that wait for a texted code or an emailed link are offered. Locally the code comes
 * back in the API response; a public demo neither sends it nor returns it.
 */
export const CAN_VERIFY_BY_MESSAGE = !PUBLIC_DEMO;
