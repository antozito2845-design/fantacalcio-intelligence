import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ConfidenceScore, DifficultyIndicator, RoleBadge } from './index';
describe('Primitive di dominio', () => {
  it.each([
    ['P', 'Portiere'],
    ['D', 'Difensore'],
    ['C', 'Centrocampista'],
    ['A', 'Attaccante'],
  ] as const)('descrive il ruolo %s', (role, label) => {
    render(<RoleBadge playerRole={role} />);
    expect(screen.getByLabelText(`Ruolo: ${label}`)).toBeVisible();
  });
  it.each([
    ['EASY', 'Facile'],
    ['MEDIUM', 'Media'],
    ['HARD', 'Difficile'],
    ['UNKNOWN', 'Non disponibile'],
  ] as const)('mostra difficoltà %s', (level, label) => {
    render(<DifficultyIndicator level={level} />);
    expect(screen.getByLabelText(`Difficoltà: ${label}`)).toBeVisible();
  });
  it('espone la confidenza accessibile', () => {
    render(<ConfidenceScore value={82} />);
    expect(screen.getByLabelText('Confidenza alta: 82%')).toBeVisible();
  });
});
