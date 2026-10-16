import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { BackButton } from './BackButton';

afterEach(cleanup);

function LocationProbe() {
  const location = useLocation();
  return (
    <>
      <BackButton fallbackTo="/players" />
      <output data-testid="location">{location.pathname}</output>
    </>
  );
}

describe('BackButton', () => {
  it('usa la cronologia browser/router prima del fallback', async () => {
    render(
      <MemoryRouter initialEntries={['/squad', '/players/7']} initialIndex={1}>
        <LocationProbe />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Indietro' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/squad');
  });

  it('usa l’origine esplicita o la lista predefinita senza cronologia', async () => {
    render(
      <MemoryRouter initialEntries={[{ pathname: '/players/7', state: { from: '/squad' } }]}>
        <LocationProbe />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Indietro' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/squad');
  });
});
