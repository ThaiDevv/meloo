import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { act } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { useSessionStore } from '@/shared/session/store'
import { SocialLoginButtons } from './SocialLoginButtons'

function renderButtons(returnTo?: string) {
  return render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter>
        <SocialLoginButtons returnTo={returnTo} />
      </MemoryRouter>
    </I18nextProvider>,
  )
}

afterEach(() => {
  act(() => {
    useSessionStore.getState().setRole(null)
  })
  // Reset env variable mock
  import.meta.env.VITE_FEATURE_FACEBOOK = undefined
})

describe('SocialLoginButtons', () => {
  it('renders Google login button', () => {
    renderButtons()
    expect(screen.getByTestId('btn-login-google')).toBeInTheDocument()
  })

  it('does NOT render Facebook button when VITE_FEATURE_FACEBOOK is not set', () => {
    import.meta.env.VITE_FEATURE_FACEBOOK = undefined
    renderButtons()
    expect(screen.queryByTestId('btn-login-facebook')).not.toBeInTheDocument()
  })

  it('renders Facebook button when VITE_FEATURE_FACEBOOK is "true"', () => {
    import.meta.env.VITE_FEATURE_FACEBOOK = 'true'
    renderButtons()
    expect(screen.getByTestId('btn-login-facebook')).toBeInTheDocument()
  })
})
