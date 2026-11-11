import { useTranslation } from 'react-i18next'
import { SocialLoginButtons } from '@/features/auth/social/SocialLoginButtons'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/Card'

/** Guests can read the boards but never appear on them (BR-20): invite them to sign in. */
export function GuestInvite() {
  const { t } = useTranslation('leaderboard')
  return (
    <Card data-testid="leaderboard-guest-invite">
      <CardHeader>
        <CardTitle as="h2">{t('guest.title')}</CardTitle>
        <CardDescription>{t('guest.description')}</CardDescription>
      </CardHeader>
      <CardContent className="max-w-xs">
        <SocialLoginButtons returnTo="/leaderboard" />
      </CardContent>
    </Card>
  )
}
