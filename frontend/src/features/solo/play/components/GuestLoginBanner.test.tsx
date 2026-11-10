import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useSessionStore } from '@/shared/session/store'
import { GuestLoginBanner } from './GuestLoginBanner'

describe('GuestLoginBanner (solo)', () => {
  beforeEach(() => {
    useSessionStore.getState().setRole(null)
  })

  it('renders invitation banner when role is GUEST', () => {
    useSessionStore.getState().setRole('GUEST')
    render(
      <MemoryRouter>
        <GuestLoginBanner />
      </MemoryRouter>,
    )

    expect(screen.getByTestId('guest-login-banner')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(/lưu kết quả/i)
    expect(screen.getByRole('button', { name: /google/i })).toBeInTheDocument()
  })

  it('renders nothing when role is USER', () => {
    useSessionStore.getState().setRole('USER')
    const { container } = render(
      <MemoryRouter>
        <GuestLoginBanner />
      </MemoryRouter>,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when role is null', () => {
    useSessionStore.getState().setRole(null)
    const { container } = render(
      <MemoryRouter>
        <GuestLoginBanner />
      </MemoryRouter>,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
