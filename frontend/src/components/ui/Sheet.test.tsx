import { useState } from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Sheet } from './Sheet';

// Like the real callers: a new onClose function on every render.
function RenameDialog({ onClosed = () => {} }: { onClosed?: () => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Rename
      </button>
      <Sheet
        open={open}
        onClose={() => {
          setOpen(false);
          onClosed();
        }}
        title="Rename card"
      >
        <input aria-label="Name" value={name} onChange={(event) => setName(event.target.value)} />
        <input aria-label="Note" />
      </Sheet>
    </>
  );
}

// The sheet focuses on the next animation frame.
const nextFrame = () => act(() => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())));

async function openDialog() {
  render(<RenameDialog />);
  const opener = screen.getByText('Rename');
  opener.focus();
  fireEvent.click(opener);
  await nextFrame();
  return opener;
}

describe('Sheet focus', () => {
  afterEach(cleanup);

  it('focuses the first field on open, not the Close button', async () => {
    await openDialog();
    expect(document.activeElement).toBe(screen.getByLabelText('Name'));
  });

  it('leaves focus where it is when the parent re-renders', async () => {
    await openDialog();
    const note = screen.getByLabelText('Note');
    note.focus();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Travel' } });
    await nextFrame();

    expect(document.activeElement).toBe(note);
  });

  it('returns focus to the opener on close', async () => {
    const opener = await openDialog();
    fireEvent.click(screen.getByLabelText('Close'));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it('closes on Escape using the latest onClose', async () => {
    const onClosed = vi.fn();
    render(<RenameDialog onClosed={onClosed} />);
    fireEvent.click(screen.getByText('Rename'));
    await nextFrame();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'x' } });

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onClosed).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});
