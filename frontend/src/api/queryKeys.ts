export const queryKeys = {
  system: ['system'] as const,
  seasons: ['seasons'] as const,
  clubs: ['clubs'] as const,
  players: (season?: string) => ['players', season ?? 'current'] as const,
  auction: (season?: string) => ['auction', season ?? 'current'] as const,
  fixtures: (season?: string, matchday = 1) => ['fixtures', season ?? 'current', matchday] as const,
  lineup: (season?: string, matchday = 1, squadId?: number) =>
    ['lineup', season ?? 'current', matchday, squadId ?? 'no-squad'] as const,
  scouting: (season?: string) => ['scouting', season ?? 'current'] as const,
  admin: (season?: string) => ['admin', season ?? 'current'] as const,
  squad: ['squad'] as const,
  squadAnalysis: ['squad-analysis'] as const,
  leagueConfiguration: ['league-configuration'] as const,
  squadCandidates: (search: string) => ['squad-candidates', search] as const,
  auctionSession: (sessionId?: string) => ['auction-session', sessionId ?? 'launcher'] as const,
};
