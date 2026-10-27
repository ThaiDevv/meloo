import type { CSSProperties } from 'react'
import { cn } from '@/shared/lib/utils'
import { avatarById, type AvatarId } from './avatars'

const sizes = { md: 'size-12', lg: 'size-16' } as const
const iconSizes = { md: 'size-6', lg: 'size-8' } as const

interface AvatarBadgeProps {
  avatarId: AvatarId
  size?: keyof typeof sizes
  className?: string
}

/** The picked avatar, decorative: the person's name sits next to it wherever it is shown. */
export function AvatarBadge({ avatarId, size = 'md', className }: AvatarBadgeProps) {
  const { icon: Icon, hue } = avatarById(avatarId)
  return (
    <span
      aria-hidden="true"
      style={{ '--h': hue } as CSSProperties}
      className={cn(
        'inline-flex shrink-0 items-center justify-center rounded-full bg-[hsl(var(--h)_45%_88%)] text-[hsl(var(--h)_60%_22%)] dark:bg-[hsl(var(--h)_35%_20%)] dark:text-[hsl(var(--h)_70%_80%)]',
        sizes[size],
        className,
      )}
    >
      <Icon className={iconSizes[size]} strokeWidth={1.75} />
    </span>
  )
}
