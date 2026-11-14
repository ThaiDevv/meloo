import {
  AudioLines,
  Disc3,
  Drum,
  Guitar,
  Headphones,
  MicVocal,
  Piano,
  Radio,
  type LucideIcon,
} from 'lucide-react'

export interface AvatarOption {
  id: AvatarId
  icon: LucideIcon
  /** Hue 0-359 of the tint, same recipe as shared/ui Avatar. */
  hue: number
}

export const AVATAR_IDS = [
  'guitar',
  'drum',
  'headphones',
  'disc',
  'mic',
  'piano',
  'radio',
  'wave',
] as const

export type AvatarId = (typeof AVATAR_IDS)[number]

export const AVATARS: readonly AvatarOption[] = [
  { id: 'guitar', icon: Guitar, hue: 12 },
  { id: 'drum', icon: Drum, hue: 38 },
  { id: 'headphones', icon: Headphones, hue: 150 },
  { id: 'disc', icon: Disc3, hue: 195 },
  { id: 'mic', icon: MicVocal, hue: 260 },
  { id: 'piano', icon: Piano, hue: 300 },
  { id: 'radio', icon: Radio, hue: 335 },
  { id: 'wave', icon: AudioLines, hue: 95 },
]

export function avatarById(id: AvatarId): AvatarOption {
  // AVATARS is declared for every id in AVATAR_IDS, so the fallback is only for the type checker.
  return AVATARS.find((a) => a.id === id) ?? (AVATARS[0] as AvatarOption)
}
