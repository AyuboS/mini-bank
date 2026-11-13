import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ForgotPasswordPage } from './ForgotPassword';
import { LoginPage } from './Login';
import { RegisterPage } from './Register';

// A public demo build, as made with VITE_DEMO_MODE=true.
vi.mock('../utils/deployment', () => ({
  PUBLIC_DEMO: true,
  SHOW_DEMO_ACCOUNTS: true,
  CAN_VERIFY_BY_MESSAGE: false,
}));

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ login: vi.fn(), loginWithPhone: vi.fn(), register: vi.fn() }),
}));

function show(page: React.ReactElement) {
  render(<MemoryRouter>{page}</MemoryRouter>);
}

describe('a public demo build', () => {
  afterEach(cleanup);

  it('signs in by password only, and says why', () => {
    show(<LoginPage />);
    expect(screen.queryByRole('tab', { name: 'Phone' })).toBeNull();
    expect(screen.queryByText('Send code')).toBeNull();
    expect(screen.getByLabelText('Username, email or phone')).toBeTruthy();
    expect(screen.getByText(/sends no text messages/)).toBeTruthy();
  });

  it('registers without a phone code', () => {
    show(<RegisterPage />);
    expect(screen.getByText('A few details')).toBeTruthy();
    expect(screen.queryByRole('tab', { name: 'Phone' })).toBeNull();
  });

  it('explains that a password reset is not available', () => {
    show(<ForgotPasswordPage />);
    expect(screen.getByText(/this demo sends none/)).toBeTruthy();
    expect(screen.queryByLabelText(/Phone number/)).toBeNull();
  });
});
