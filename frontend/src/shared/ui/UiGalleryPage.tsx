import { Plus } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Avatar } from './Avatar'
import { Badge } from './Badge'
import { Button } from './Button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './Card'
import { Dialog } from './Dialog'
import { EmptyState } from './EmptyState'
import { ErrorState } from './ErrorState'
import { Input } from './Input'
import { Skeleton } from './Skeleton'
import { Tabs, TabsContent, TabsList, TabsTrigger } from './Tabs'
import { toast } from './toast-store'

const swatches = [
  ['background', 'bg-background'],
  ['surface', 'bg-surface'],
  ['raised', 'bg-raised'],
  ['primary', 'bg-primary'],
  ['positive', 'bg-positive'],
  ['negative', 'bg-negative'],
  ['warning', 'bg-warning'],
] as const

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">{title}</h2>
      {children}
    </section>
  )
}

/** Component gallery for review on dev and preview builds (route `/_ui`). */
export function UiGalleryPage() {
  const { t } = useTranslation('common')
  const [dialogOpen, setDialogOpen] = useState(false)
  const hintId = useId()

  return (
    <div className="mx-auto w-full max-w-6xl space-y-12 px-4 py-10">
      <header className="space-y-2">
        <h1 className="font-display text-3xl font-bold tracking-tight">{t('title')}</h1>
        <p className="max-w-prose text-muted-foreground">{t('gallery.intro')}</p>
      </header>

      <Section title={t('gallery.colors.title')}>
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          {swatches.map(([key, bg]) => (
            <li key={key} className="space-y-2">
              <div className={`h-16 rounded-xl border border-border ${bg}`} />
              <p className="text-sm text-muted-foreground">{t(`gallery.colors.${key}`)}</p>
            </li>
          ))}
        </ul>
      </Section>

      <Section title={t('gallery.buttons.title')}>
        <div className="flex flex-wrap items-center gap-3">
          <Button>{t('gallery.buttons.primary')}</Button>
          <Button variant="outline">{t('gallery.buttons.outline')}</Button>
          <Button variant="ghost">{t('gallery.buttons.ghost')}</Button>
          <Button variant="destructive">{t('gallery.buttons.destructive')}</Button>
          <Button size="sm">{t('gallery.buttons.small')}</Button>
          <Button size="icon" variant="outline" aria-label={t('gallery.buttons.icon')}>
            <Plus aria-hidden="true" className="size-[18px]" strokeWidth={1.75} />
          </Button>
          <Button disabled>{t('gallery.buttons.disabled')}</Button>
        </div>
      </Section>

      <div className="grid items-start gap-x-16 gap-y-12 lg:grid-cols-2">
        <Section title={t('gallery.fields.title')}>
          <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
            <label className="space-y-1.5 text-sm font-medium">
              {t('gallery.fields.label')}
              <Input placeholder={t('gallery.fields.placeholder')} />
            </label>
            <div className="space-y-1.5">
              <label htmlFor={`${hintId}-code`} className="text-sm font-medium">
                {t('gallery.fields.invalidLabel')}
              </label>
              <Input
                id={`${hintId}-code`}
                defaultValue={t('gallery.fields.invalidValue')}
                aria-invalid="true"
                aria-describedby={hintId}
              />
              <p id={hintId} className="text-sm text-negative">
                {t('gallery.fields.invalidHint')}
              </p>
            </div>
          </div>
        </Section>

        <Section title={t('gallery.cards.title')}>
          <Card className="max-w-md">
            <CardHeader>
              <CardTitle>{t('gallery.cards.cardTitle')}</CardTitle>
              <CardDescription>{t('gallery.cards.cardBody')}</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              <Badge>{t('gallery.cards.neutral')}</Badge>
              <Badge tone="positive">{t('gallery.cards.positive')}</Badge>
              <Badge tone="negative">{t('gallery.cards.negative')}</Badge>
              <Badge tone="warning">{t('gallery.cards.warning')}</Badge>
            </CardContent>
          </Card>
        </Section>

        <Section title={t('gallery.avatars.title')}>
          <div className="flex items-center gap-3">
            <Avatar name={t('gallery.avatars.a')} size="sm" />
            <Avatar name={t('gallery.avatars.b')} />
            <Avatar name={t('gallery.avatars.c')} size="lg" />
            <Avatar name={t('gallery.avatars.a')} src="/missing-avatar.png" />
          </div>
        </Section>

        <Section title={t('gallery.tabs.title')}>
          <Tabs defaultValue="solo" className="max-w-xl">
            <TabsList aria-label={t('gallery.tabs.label')}>
              <TabsTrigger value="solo">{t('gallery.tabs.solo')}</TabsTrigger>
              <TabsTrigger value="daily">{t('gallery.tabs.daily')}</TabsTrigger>
              <TabsTrigger value="room">{t('gallery.tabs.room')}</TabsTrigger>
            </TabsList>
            <TabsContent value="solo">{t('gallery.tabs.soloBody')}</TabsContent>
            <TabsContent value="daily">{t('gallery.tabs.dailyBody')}</TabsContent>
            <TabsContent value="room">{t('gallery.tabs.roomBody')}</TabsContent>
          </Tabs>
        </Section>

        <Section title={t('gallery.dialog.title')}>
          <Button
            variant="outline"
            onClick={() => {
              setDialogOpen(true)
            }}
          >
            {t('gallery.dialog.open')}
          </Button>
          <Dialog
            open={dialogOpen}
            onClose={() => {
              setDialogOpen(false)
            }}
            title={t('gallery.dialog.heading')}
            description={t('gallery.dialog.body')}
          >
            <div className="flex justify-end">
              <Button
                variant="destructive"
                onClick={() => {
                  setDialogOpen(false)
                }}
              >
                {t('gallery.dialog.confirm')}
              </Button>
            </div>
          </Dialog>
        </Section>

        <Section title={t('gallery.toasts.title')}>
          <div className="flex flex-wrap gap-3">
            <Button
              variant="outline"
              onClick={() => toast({ title: t('gallery.toasts.neutralTitle') })}
            >
              {t('gallery.toasts.neutral')}
            </Button>
            <Button
              variant="outline"
              onClick={() => toast({ title: t('gallery.toasts.positiveTitle'), tone: 'positive' })}
            >
              {t('gallery.toasts.positive')}
            </Button>
            <Button
              variant="outline"
              onClick={() =>
                toast({
                  title: t('gallery.toasts.negativeTitle'),
                  description: t('gallery.toasts.negativeBody'),
                  tone: 'negative',
                })
              }
            >
              {t('gallery.toasts.negative')}
            </Button>
          </div>
        </Section>

        <Section title={t('gallery.loading.title')}>
          <Card className="max-w-md" aria-busy="true">
            <CardContent className="flex items-center gap-4">
              <Skeleton className="size-10 rounded-full" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-3 w-3/4" />
              </div>
            </CardContent>
          </Card>
        </Section>

        <Section title={t('gallery.empty.title')}>
          <EmptyState
            className="max-w-xl"
            title={t('gallery.empty.heading')}
            description={t('gallery.empty.body')}
            action={<Button size="sm">{t('gallery.empty.action')}</Button>}
          />
        </Section>

        <Section title={t('gallery.error.title')}>
          <ErrorState
            className="max-w-xl"
            onRetry={() => toast({ title: t('ui.retry'), tone: 'positive' })}
          />
        </Section>
      </div>
    </div>
  )
}
