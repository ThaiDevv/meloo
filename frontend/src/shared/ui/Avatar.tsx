import { useState, type CSSProperties } from 'react'
import { cn } from '@/shared/lib/utils'
import { AVATARS, type AvatarId } from './AvatarPresets'

const sizes = { sm: 'size-8 text-xs', md: 'size-10 text-sm', lg: 'size-14 text-lg' } as const

/** Stable hue (0-359) from a name, so a person keeps the same tint everywhere. */
function hueOf(name: string): number {
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) % 360
  return hash
}

interface AvatarProps {
  /** Display name: the image alt text and the source of the initial. */
  name: string
  src?: string
  avatarId?: AvatarId | null
  size?: keyof typeof sizes
  className?: string
}

export function Avatar({ name, src, avatarId, size = 'md', className }: AvatarProps) {
  const [failedSrc, setFailedSrc] = useState<string>()
  const box = cn(
    'inline-flex shrink-0 items-center justify-center rounded-full',
    sizes[size],
    className,
  )
  const preset = AVATARS.find((avatar) => avatar.id === avatarId)
  if (preset) {
    const Icon = preset.icon
    return (
      <span
        role="img"
        aria-label={name}
        data-avatar-id={preset.id}
        style={{ '--h': preset.hue } as CSSProperties}
        className={cn(
          box,
          'bg-[hsl(var(--h)_45%_88%)] text-[hsl(var(--h)_60%_22%)] dark:bg-[hsl(var(--h)_35%_20%)] dark:text-[hsl(var(--h)_70%_80%)]',
        )}
      >
        <Icon aria-hidden="true" className="size-1/2" strokeWidth={1.75} />
      </span>
    )
  }
  if (src && src !== failedSrc) {
    return (
      <img
        src={src}
        alt={name}
        className={cn(box, 'object-cover')}
        onError={() => {
          setFailedSrc(src)
        }}
      />
    )
  }
  const first = name.trim().codePointAt(0)
  const initial = first === undefined ? '?' : String.fromCodePoint(first).toUpperCase()
  return (
    <span
      role="img"
      aria-label={name}
      style={{ '--h': hueOf(name) } as CSSProperties}
      className={cn(
        box,
        'bg-[hsl(var(--h)_45%_88%)] font-semibold text-[hsl(var(--h)_60%_22%)] dark:bg-[hsl(var(--h)_35%_20%)] dark:text-[hsl(var(--h)_70%_80%)]',
      )}
    >
      <span aria-hidden="true">{initial}</span>
    </span>
  )
}
