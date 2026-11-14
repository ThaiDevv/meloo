import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/shared/api/authStore'
import { apiClient } from '@/shared/api/client'
import { Avatar } from '@/shared/ui/Avatar'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { AvatarPicker } from './guest/AvatarPicker'
import { guestFormSchema, NICKNAME_MAX, NICKNAME_MIN, type GuestFormValues } from './guest/nickname'

export function ProfileSetup() {
  const { t } = useTranslation('auth')
  const user = useAuthStore((s) => s.user)
  const dialog = useRef<HTMLDialogElement>(null)
  const pending = useRef(false)
  const [saving, setSaving] = useState(false)
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<GuestFormValues>({
    resolver: zodResolver(guestFormSchema),
    defaultValues: { nickname: '', avatarId: 'guitar' },
  })
  const avatarId = useWatch({ control, name: 'avatarId' })
  const nickname = useWatch({ control, name: 'nickname' })

  useEffect(() => {
    const element = dialog.current
    if (user?.kind === 'REGISTERED' && user.profileCompleted === false && element && !element.open)
      element.showModal()
    else if (element?.open && (user?.kind !== 'REGISTERED' || user.profileCompleted !== false))
      element.close()
  }, [user])

  async function submit(values: GuestFormValues) {
    if (pending.current) return
    pending.current = true
    setSaving(true)
    try {
      const { data, response } = await apiClient.PATCH('/api/v1/me/profile', { body: values })
      if (!data || !response.ok) {
        setError(response.status === 400 ? 'nickname' : 'root', {
          message: response.status === 400 ? 'guest.error.rejected' : 'setup.error',
        })
        return
      }
      const current = useAuthStore.getState().user
      if (current && current.id === user?.id) {
        useAuthStore.getState().updateUser({
          ...current,
          nickname: data.nickname,
          avatarId: data.avatarId,
          profileCompleted: data.profileCompleted,
        })
        await queryClient.invalidateQueries({ queryKey: ['profile'] })
      }
    } catch {
      setError('root', { message: 'setup.error' })
    } finally {
      pending.current = false
      setSaving(false)
    }
  }

  return (
    <dialog
      ref={dialog}
      onCancel={(event) => {
        event.preventDefault()
      }}
      aria-labelledby="setup-title"
      className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-2xl bg-surface p-6 text-foreground backdrop:bg-black/70 sm:p-8"
    >
      <form
        noValidate
        onSubmit={(event) => {
          void handleSubmit(submit)(event)
        }}
        className="space-y-6"
      >
        <div>
          <h1 id="setup-title" className="font-display text-2xl font-bold">
            {t('setup.title')}
          </h1>
          <p className="mt-2 text-muted-foreground">{t('setup.description')}</p>
        </div>
        <div className="flex items-center gap-3">
          <Avatar name={nickname || t('setup.preview')} avatarId={avatarId} size="lg" />
          <span>{t('setup.preview')}</span>
        </div>
        <div className="space-y-2">
          <label htmlFor="setup-nickname" className="block text-sm font-semibold">
            {t('guest.nickname.label')}
          </label>
          <Input
            id="setup-nickname"
            autoComplete="nickname"
            autoFocus
            maxLength={NICKNAME_MAX * 2}
            aria-invalid={!!errors.nickname}
            aria-describedby={errors.nickname ? 'setup-hint setup-error' : 'setup-hint'}
            {...register('nickname')}
          />
          <p id="setup-hint" className="text-sm text-muted-foreground">
            {t('guest.nickname.hint', { min: NICKNAME_MIN, max: NICKNAME_MAX })}
          </p>
          {errors.nickname?.message && (
            <p id="setup-error" role="alert" className="text-negative">
              {t(errors.nickname.message, { min: NICKNAME_MIN, max: NICKNAME_MAX })}
            </p>
          )}
        </div>
        <AvatarPicker field={register('avatarId')} />
        {errors.root?.message && (
          <p role="alert" className="text-negative">
            {t(errors.root.message)}
          </p>
        )}
        <Button type="submit" disabled={saving} className="w-full">
          {saving ? t('setup.saving') : t('setup.submit')}
        </Button>
      </form>
    </dialog>
  )
}
