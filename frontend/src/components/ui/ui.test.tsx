import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button, Input, SearchInput, Tabs } from './index';
describe('UI primitives', () => {
  it('Button rende nome, click e disabled', async () => {
    const click = vi.fn();
    const { rerender } = render(<Button onClick={click}>Conferma</Button>);
    await userEvent.click(screen.getByRole('button', { name: 'Conferma' }));
    expect(click).toHaveBeenCalledOnce();
    rerender(<Button disabled>Conferma</Button>);
    expect(screen.getByRole('button', { name: 'Conferma' })).toBeDisabled();
  });
  it('Input collega label e consente input', async () => {
    render(<Input label="Nome" />);
    await userEvent.type(screen.getByRole('textbox', { name: 'Nome' }), 'Lega');
    expect(screen.getByRole('textbox', { name: 'Nome' })).toHaveValue('Lega');
  });
  it('SearchInput supporta tastiera', async () => {
    render(<SearchInput label="Cerca profilo" />);
    await userEvent.type(screen.getByRole('searchbox', { name: 'Cerca profilo' }), 'ala{enter}');
    expect(screen.getByRole('searchbox')).toHaveValue('ala');
  });
  it('Tabs cambia selezione', async () => {
    render(<Tabs labels={['Uno', 'Due']} />);
    await userEvent.click(screen.getByRole('tab', { name: 'Due' }));
    expect(screen.getByRole('tab', { name: 'Due' })).toHaveAttribute('aria-selected', 'true');
  });
});
