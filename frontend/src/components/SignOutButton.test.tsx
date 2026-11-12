import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api/auth';
import { tokenStore } from '../api/client';
import type { AuthenticationResponse } from '../api/types';
import { AuthProvider } from '../auth/AuthContext';
import { SignOutButton } from './SignOutButton';

function renderButton() {
  render(
    <MemoryRouter initialEntries={['/profile']}>
      <AuthProvider>
        <Routes>
          <Route path="/profile" element={<SignOutButton />} />
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('SignOutButton', () => {
  afterEach(() => {
    cleanup();
    localStorage.clear();
  });

  it('signs out after confirmation', async () => {
    const logout = vi.spyOn(authApi, 'logout').mockResolvedValue();
    renderButton();
    tokenStore.save({ accessToken: 'access', refreshToken: 'refresh' } as AuthenticationResponse);

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(logout).not.toHaveBeenCalled();

    const dialog = screen.getByRole('dialog');
    const confirm = Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === 'Sign out')!;
    await act(async () => {
      fireEvent.click(confirm);
    });

    expect(logout).toHaveBeenCalledTimes(1);
    expect(tokenStore.accessToken()).toBeNull();
    expect(screen.getByText('Login page')).toBeTruthy();
  });

  it('stays signed in when the confirmation is dismissed', () => {
    const logout = vi.spyOn(authApi, 'logout').mockResolvedValue();
    renderButton();

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    fireEvent.click(screen.getByText('Stay signed in'));

    expect(logout).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
