export type AuctionCategory = 'TOP' | 'SEMI-TOP' | 'SCOMMESSA' | 'NORMALE';

export type AuctionCategoryPlayer = {
  fantasyRole: string;
  intelligenceScore: number;
  potentialScore: number | null;
};

export type RoleThreshold = {
  top: number;
  semiTop: number;
  potentialBet: number | null;
};

export type RoleThresholds = Record<string, RoleThreshold>;

function percentile(values: number[], percentileValue: number): number | null {
  const sorted = values
    .filter((value) => Number.isFinite(value))
    .slice()
    .sort((a, b) => a - b);

  if (sorted.length === 0) {
    return null;
  }

  if (sorted.length === 1) {
    return sorted[0];
  }

  const index = (sorted.length - 1) * percentileValue;
  const lower = Math.floor(index);
  const upper = Math.ceil(index);

  if (lower === upper) {
    return sorted[lower];
  }

  const weight = index - lower;

  return sorted[lower] + (sorted[upper] - sorted[lower]) * weight;
}

export function buildRoleThresholds(players: AuctionCategoryPlayer[]): RoleThresholds {
  const roles = new Set(players.map((player) => player.fantasyRole));
  const thresholds: RoleThresholds = {};

  for (const role of roles) {
    const rolePlayers = players.filter((player) => player.fantasyRole === role);

    const scores = rolePlayers.map((player) => player.intelligenceScore);

    const potentials = rolePlayers
      .map((player) => player.potentialScore)
      .filter((value): value is number => value !== null && Number.isFinite(value));

    const top = percentile(scores, 0.9);
    const semiTop = percentile(scores, 0.75);
    const potentialBet = percentile(potentials, 0.75);

    if (top === null || semiTop === null) {
      continue;
    }

    thresholds[role] = {
      top,
      semiTop,
      potentialBet,
    };
  }

  return thresholds;
}

export function classifyAuctionPlayer(
  player: AuctionCategoryPlayer,
  thresholds: RoleThresholds,
): {
  category: AuctionCategory;
  reason: string;
} {
  const roleThreshold = thresholds[player.fantasyRole];

  if (!roleThreshold) {
    return {
      category: 'NORMALE',
      reason: 'Campione ruolo insufficiente per una classificazione relativa.',
    };
  }

  if (player.intelligenceScore >= roleThreshold.top) {
    return {
      category: 'TOP',
      reason: 'FantaScore nel top 10% dei profili del proprio ruolo.',
    };
  }

  if (player.intelligenceScore >= roleThreshold.semiTop) {
    return {
      category: 'SEMI-TOP',
      reason: 'FantaScore nel top 25% dei profili del proprio ruolo.',
    };
  }

  if (
    player.potentialScore !== null &&
    roleThreshold.potentialBet !== null &&
    player.potentialScore >= roleThreshold.potentialBet
  ) {
    return {
      category: 'SCOMMESSA',
      reason: 'Potenziale nel top 25% del ruolo senza uno status TOP o SEMI-TOP attuale.',
    };
  }

  return {
    category: 'NORMALE',
    reason: 'Profilo nella fascia ordinaria del proprio ruolo.',
  };
}
