import { Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useThemeStore } from '@/shared/theme/store'
import { Button } from './Button'

export function ThemeToggle() {
  const { t } = useTranslation('common')
  const theme = useThemeStore((s) => s.theme)
  const toggle = useThemeStore((s) => s.toggle)
  const Icon = theme === 'dark' ? Sun : Moon
  return (
    <Button
      variant="ghost"
      size="icon"
      aria-label={theme === 'dark' ? t('theme.switchToLight') : t('theme.switchToDark')}
      onClick={toggle}
    >
      <Icon aria-hidden="true" className="size-[18px]" strokeWidth={1.75} />
    </Button>
  )
}
