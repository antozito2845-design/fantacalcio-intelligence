import { describe, expect, it } from 'vitest';
import {
  buildRoleThresholds,
  classifyAuctionPlayer,
  type AuctionCategoryPlayer,
} from './auctionCategory';

const players: AuctionCategoryPlayer[] = [
  { fantasyRole: 'A', intelligenceScore: 10, potentialScore: 10 },
  { fantasyRole: 'A', intelligenceScore: 20, potentialScore: 20 },
  { fantasyRole: 'A', intelligenceScore: 30, potentialScore: 30 },
  { fantasyRole: 'A', intelligenceScore: 40, potentialScore: 40 },
  { fantasyRole: 'A', intelligenceScore: 50, potentialScore: 50 },
  { fantasyRole: 'A', intelligenceScore: 60, potentialScore: 60 },
  { fantasyRole: 'A', intelligenceScore: 70, potentialScore: 70 },
  { fantasyRole: 'A', intelligenceScore: 80, potentialScore: 80 },
  { fantasyRole: 'A', intelligenceScore: 90, potentialScore: 90 },
  { fantasyRole: 'A', intelligenceScore: 100, potentialScore: 100 },

  { fantasyRole: 'C', intelligenceScore: 10, potentialScore: 90 },
  { fantasyRole: 'C', intelligenceScore: 20, potentialScore: 20 },
  { fantasyRole: 'C', intelligenceScore: 30, potentialScore: 30 },
  { fantasyRole: 'C', intelligenceScore: 40, potentialScore: 40 },
  { fantasyRole: 'C', intelligenceScore: 50, potentialScore: 50 },
  { fantasyRole: 'C', intelligenceScore: 60, potentialScore: 60 },
  { fantasyRole: 'C', intelligenceScore: 70, potentialScore: 70 },
  { fantasyRole: 'C', intelligenceScore: 80, potentialScore: 80 },
  { fantasyRole: 'C', intelligenceScore: 90, potentialScore: 90 },
  { fantasyRole: 'C', intelligenceScore: 100, potentialScore: 100 },
];

describe('auctionCategory', () => {
  it('calcola soglie separate per ruolo', () => {
    const thresholds = buildRoleThresholds(players);

    expect(thresholds.A).toBeDefined();
    expect(thresholds.C).toBeDefined();

    expect(thresholds.A!.top).toBeGreaterThan(thresholds.A!.semiTop);
    expect(thresholds.C!.top).toBeGreaterThan(thresholds.C!.semiTop);
  });

  it('classifica TOP e SEMI-TOP rispetto al proprio ruolo', () => {
    const thresholds = buildRoleThresholds(players);

    expect(
      classifyAuctionPlayer(
        { fantasyRole: 'A', intelligenceScore: 100, potentialScore: 50 },
        thresholds,
      ).category,
    ).toBe('TOP');

    expect(
      classifyAuctionPlayer(
        { fantasyRole: 'A', intelligenceScore: 80, potentialScore: 50 },
        thresholds,
      ).category,
    ).toBe('SEMI-TOP');
  });

  it('classifica SCOMMESSA solo quando il potenziale è disponibile e alto', () => {
    const thresholds = buildRoleThresholds(players);

    expect(
      classifyAuctionPlayer(
        { fantasyRole: 'C', intelligenceScore: 10, potentialScore: 90 },
        thresholds,
      ).category,
    ).toBe('SCOMMESSA');

    expect(
      classifyAuctionPlayer(
        { fantasyRole: 'C', intelligenceScore: 10, potentialScore: null },
        thresholds,
      ).category,
    ).toBe('NORMALE');
  });

  it('non converte i nullable in zero', () => {
    const thresholds = buildRoleThresholds(players);

    const result = classifyAuctionPlayer(
      {
        fantasyRole: 'A',
        intelligenceScore: 20,
        potentialScore: null,
      },
      thresholds,
    );

    expect(result.category).toBe('NORMALE');
  });
});
