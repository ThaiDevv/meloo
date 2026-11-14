import { LogOut, User } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'
import { apiClient } from '@/shared/api/client'
import { useAuthStore } from '@/shared/api/authStore'
import { useSessionStore } from '@/shared/session/store'
import { toast } from '@/shared/ui/toast-store'
import { Button } from '@/shared/ui/Button'
import { ProfileSetup } from './ProfileSetup'
import { SocialLoginButtons } from './social/SocialLoginButtons'
import { useSessionRestore } from './useSessionRestore'

/**
 * Account menu in the header. Replaces the stub (`return null`) from FE-01.
 *
 * - Not signed in (role === null):
 *   - While checking session restore: shows skeleton placeholder.
 *   - Once done or failed: shows "Sign in" button that opens an inline panel.
 * - Guest (role === 'GUEST'): shows "Sign in" button to encourage upgrade.
 * - Registered / Admin (role === 'USER' | 'ADMIN'): dropdown with Profile and Sign out.
 */
export function AccountMenu() {
  const user = useAuthStore((s) => s.user)
  return (
    <>
      <AccountMenuContent />
      {user?.kind === 'REGISTERED' && user.profileCompleted === false && <ProfileSetup />}
    </>
  )
}

function AccountMenuContent() {
  const role = useSessionStore((s) => s.role)
  const { state } = useSessionRestore()
  const location = useLocation()

  if (role === 'USER' || role === 'ADMIN') {
    return <RegisteredMenu />
  }

  if (role === null && state === 'checking') {
    return (
      <div
        className="h-9 w-20 animate-pulse rounded-lg bg-raised/50"
        data-testid="account-menu-skeleton"
        aria-hidden="true"
      />
    )
  }

  // Guest or unauthenticated (state === 'done' or 'failed'): show login button
  return <LoginPanel returnTo={location.pathname} />
}

/** Dropdown menu for signed-in users: Profile link + Sign out. */
function RegisteredMenu() {
  const { t } = useTranslation('auth')
  const [open, setOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }

    function handleClickOutside(e: MouseEvent | TouchEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open])

  async function handleLogout() {
    if (loggingOut) return
    setLoggingOut(true)
    try {
      const { response } = await apiClient.POST('/api/v1/auth/logout')
      if (response.ok) {
        useAuthStore.getState().clear()
        setOpen(false)
      } else {
        toast({ title: t('accountMenu.logoutError'), tone: 'negative' })
      }
    } catch {
      toast({ title: t('accountMenu.logoutError'), tone: 'negative' })
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="relative" ref={menuRef}>
      <Button
        ref={triggerRef}
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

/** Login panel (button that toggles inline panel showing social login options). */
function LoginPanel({ returnTo }: { returnTo: string }) {
  const { t } = useTranslation('auth')
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.preventDefault()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }

    function handleClickOutside(e: MouseEvent | TouchEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    document.addEventListener('mousedown', handleClickOutside)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [open])

  return (
    <div className="relative" ref={containerRef}>
      <Button
        ref={triggerRef}
        type="button"
        variant="outline"
        size="sm"
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => {
          setOpen((v) => !v)
        }}
        data-testid="account-menu-login-btn"
      >
        {t('accountMenu.loginButton')}
      </Button>

      {open && (
        <div
          role="dialog"
          aria-label={t('accountMenu.loginButton')}
          className="absolute right-0 top-full z-50 mt-2 w-72 rounded-xl border border-border bg-background p-4 shadow-lg"
        >
          <SocialLoginButtons returnTo={returnTo} />
        </div>
      )}
    </div>
  )
}
