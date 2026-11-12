export interface HomeMode {
  id: 'solo' | 'daily' | 'room' | 'quick'
  /** Route the entry opens once its ticket has shipped. */
  to: string
  /** Flip to true in the ticket that builds the destination; until then the entry reads "Sắp có". */
  available: boolean
}

export const HOME_MODES: HomeMode[] = [
  { id: 'solo', to: '/solo', available: true },
  { id: 'daily', to: '/daily', available: true },
  { id: 'room', to: '/rooms/new', available: true },
  { id: 'quick', to: '/quick-match', available: true },
]
