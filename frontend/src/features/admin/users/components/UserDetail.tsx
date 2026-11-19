import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'
import { Field } from '../../shell/fields'
import { useAdminUser, useChangeUser } from '../api/usersApi'
import { ConfirmChange, QueryState } from './AdminFeedback'

export function UserDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const { t, i18n } = useTranslation('admin')
  const panel = useRef<HTMLElement>(null)
  useEffect(() => {
    panel.current?.focus()
  }, [])
  const query = useAdminUser(id)
  const mutation = useChangeUser()
  const [action, setAction] = useState<'lock' | 'unlock' | 'role' | null>(null)
  const [reason, setReason] = useState('')
  const [saved, setSaved] = useState(false)
  const user = query.data
  const begin = (next: NonNullable<typeof action>) => {
    setReason('')
    setSaved(false)
    mutation.reset()
    setAction(next)
  }
  return (
    <section
      ref={panel}
      tabIndex={-1}
      className="space-y-4 rounded-2xl border border-border bg-surface p-4"
      aria-label={t('users.detail')}
    >
      <div className="flex justify-between gap-3">
        <h3 className="font-semibold">{t('users.detail')}</h3>
        <Button variant="ghost" onClick={onClose}>
          {t('close')}
        </Button>
      </div>
      <QueryState
        pending={query.isPending}
        error={query.isError}
        empty={false}
        retry={() => void query.refetch()}
      >
        {user && (
          <>
            <h4 className="text-lg font-medium break-words">{user.nickname}</h4>
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              {[
                ['id', user.id],
                ['email', user.email ?? t('manage.none')],
                ['kind', t(`users.values.${user.kind}`)],
                ['role', t(`users.values.${user.role}`)],
                ['status', t(`users.values.${user.status}`)],
                ['rank', user.rankPoints],
                ['providers', user.linkedProviders.join(', ') || t('manage.none')],
                ['created', new Date(user.createdAt).toLocaleString(i18n.language)],
                ['active', new Date(user.lastActiveAt).toLocaleString(i18n.language)],
                ['lockReason', user.lockReason ?? t('manage.none')],
                [
                  'lockedAt',
                  user.lockedAt
                    ? new Date(user.lockedAt).toLocaleString(i18n.language)
                    : t('manage.none'),
                ],
              ].map(([key, value]) => (
                <div key={key}>
                  <dt className="text-muted-foreground">{t(`users.${String(key)}`)}</dt>
                  <dd className="mt-1 break-all">{value}</dd>
                </div>
              ))}
            </dl>
            <div className="flex flex-wrap gap-2">
              <Button
                variant={user.status === 'ACTIVE' ? 'destructive' : 'outline'}
                onClick={() => {
                  begin(user.status === 'ACTIVE' ? 'lock' : 'unlock')
                }}
              >
                {t(user.status === 'ACTIVE' ? 'users.lock' : 'users.unlock')}
              </Button>
              {user.kind === 'REGISTERED' && (
                <Button
                  variant="outline"
                  onClick={() => {
                    begin('role')
                  }}
                >
                  {t('users.changeRole')}
                </Button>
              )}
            </div>
            {saved && (
              <p role="status" className="text-sm text-positive">
                {t('manage.saved')}
              </p>
            )}
            {action && (
              <ConfirmChange
                title={t(`users.${action === 'role' ? 'changeRole' : action}`)}
                description={
                  action === 'role'
                    ? t('users.roleConfirm', {
                        name: user.nickname,
                        role: t(`users.values.${user.role === 'ADMIN' ? 'USER' : 'ADMIN'}`),
                      })
                    : t('users.lockConfirm', { name: user.nickname, action: t(`users.${action}`) })
                }
                pending={mutation.isPending}
                error={mutation.error}
                onClose={() => {
                  setAction(null)
                }}
                disabled={action === 'lock' && !reason.trim()}
                onConfirm={() => {
                  mutation.mutate(
                    action === 'role'
                      ? {
                          id,
                          type: 'role',
                          body: { role: user.role === 'ADMIN' ? 'USER' : 'ADMIN' },
                        }
                      : {
                          id,
                          type: 'lock',
                          body: {
                            locked: action === 'lock',
                            reason: action === 'lock' ? reason.trim() : null,
                          },
                        },
                    {
                      onSuccess: () => {
                        setAction(null)
                        setSaved(true)
                      },
                    },
                  )
                }}
              >
                {action === 'lock' && (
                  <Field label={t('users.lockReason')} htmlFor="user-lock-reason">
                    <Input
                      id="user-lock-reason"
                      required
                      maxLength={500}
                      value={reason}
                      onChange={(e) => {
                        setReason(e.target.value)
                      }}
                    />
                  </Field>
                )}
              </ConfirmChange>
            )}
          </>
        )}
      </QueryState>
    </section>
  )
}
