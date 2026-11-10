import { LogOut, User } from 'lucide-react'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { apiClient } from '@/shared/api/client'
import { useAuthStore } from '@/shared/api/authStore'
import { useSessionStore } from '@/shared/session/store'
import { toast } from '@/shared/ui/toast-store'
import { Button } from '@/shared/ui/Button'
import { SocialLoginButtons } from './social/SocialLoginButtons'

/**
 * Account menu in the header. Replaces the stub (`return null`) from FE-01.
 *
 * - Not signed in (role === null): shows "Sign in" button that opens an inline panel.
 * - Guest (role === 'GUEST'): same — guests are encouraged to upgrade.
 * - Registered / Admin (role === 'USER' | 'ADMIN'): dropdown with Profile and Sign out.
 */
export function AccountMenu() {
  const role = useSessionStore((s) => s.role)
  const location = useLocation()

  if (role === 'USER' || role === 'ADMIN') {
    return <RegisteredMenu />
  }

  // Guest or unauthenticated: show login button
  return <LoginPanel returnTo={location.pathname} />
}

/** Dropdown menu for signed-in users: Profile link + Sign out. */
function RegisteredMenu() {
  const { t } = useTranslation('auth')
  const [open, setOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  async function handleLogout() {
    if (loggingOut) return
    setLoggingOut(true)
    setOpen(false)
    try {
      await apiClient.POST('/api/v1/auth/logout')
      useAuthStore.getState().clear()
    } catch {
      toast({ title: t('accountMenu.logoutError'), tone: 'negative' })
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="relative" ref={menuRef}>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        aria-label={t('accountMenu.openMenu')}
        aria-expanded={open}
        aria-haspopup="menu"
        onClick={() => {
          setOpen((v) => !v)
        }}
        data-testid="account-menu-trigger"
      >
        <User className="size-5" aria-hidden="true" />
      </Button>

      {open && (
        <div
          role="menu"
          aria-label={t('accountMenu.openMenu')}
          className="absolute right-0 top-full z-50 mt-1 min-w-[10rem] rounded-xl border border-border bg-background py-1 shadow-lg"
          onBlur={(e) => {
            if (e.relatedTarget && !menuRef.current?.contains(e.relatedTarget)) {
              setOpen(false)
            }
          }}
        >
          <Link
            to="/profile"
            role="menuitem"
            className="flex items-center gap-2 px-4 py-2.5 text-sm hover:bg-raised focus:bg-raised focus:outline-none"
            onClick={() => {
              setOpen(false)
            }}
            data-testid="account-menu-profile"
          >
            <User className="size-4" aria-hidden="true" />
            {t('accountMenu.profile')}
          </Link>

          <button
            type="button"
            role="menuitem"
            className="flex w-full items-center gap-2 px-4 py-2.5 text-sm hover:bg-raised focus:bg-raised focus:outline-none disabled:opacity-50"
            disabled={loggingOut}
            onClick={() => {
              void handleLogout()
            }}
            data-testid="account-menu-logout"
          >
            <LogOut className="size-4" aria-hidden="true" />
            {loggingOut ? t('accountMenu.loggingOut') : t('accountMenu.logout')}
          </button>
        </div>
      )}
    </div>
  )
}

/** Login panel (button that expands to show social login options). */
function LoginPanel({ returnTo }: { returnTo: string }) {
  const { t } = useTranslation('auth')
  const [expanded, setExpanded] = useState(false)

  if (!expanded) {
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => {
          setExpanded(true)
        }}
        data-testid="account-menu-login-btn"
      >
        {t('accountMenu.loginButton')}
      </Button>
    )
  }

  return (
    <div className="absolute right-4 top-full z-50 mt-1 w-72 rounded-xl border border-border bg-background p-4 shadow-lg">
      <SocialLoginButtons returnTo={returnTo} />
    </div>
  )
}
