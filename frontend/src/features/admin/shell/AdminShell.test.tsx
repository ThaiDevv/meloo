import { act, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { resetSessionRestoreForTests } from '@/features/auth/useSessionRestore'
import { server } from '@/mocks/server'
import { useSessionStore, type Role } from '@/shared/session/store'
import { AdminShell } from './AdminShell'
import { renderAdmin } from './testing'

function Where({ label }: { label: string }) {
  const location = useLocation()
  return (
    <p>
      {label} {(location.state as { from?: string } | null)?.from}
    </p>
  )
}

function renderShell(role: Role | null, path = '/admin/catalog') {
  act(() => {
    useSessionStore.getState().setRole(role)
  })
  return renderAdmin(
    <Routes>
      <Route path="/admin" element={<AdminShell />}>
        <Route index element={<p>dashboard page</p>} />
        <Route path="catalog" element={<p>catalog page</p>} />
      </Route>
      <Route path="/" element={<Where label="home" />} />
      <Route path="/start" element={<Where label="start" />} />
    </Routes>,
    path,
  )
}

afterEach(() => {
  act(() => {
    useSessionStore.getState().setRole(null)
  })
  resetSessionRestoreForTests()
})

describe('AdminShell', () => {
  it('lets an admin in and shows the admin menu with the current page marked', () => {
    renderShell('ADMIN')
    expect(screen.getByText('catalog page')).toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Menu quản trị' })
    expect(menu).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kho nhạc' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Tổng quan' })).not.toHaveAttribute('aria-current')
    for (const name of ['Bộ đề', 'Người dùng', 'Kiểm duyệt', 'Nhật ký']) {
      expect(screen.getByRole('link', { name })).toBeInTheDocument()
    }
  })

  it.each(['USER', 'GUEST'] as const)('sends role %s back to the home page', (role) => {
    renderShell(role)
    expect(screen.getByText(/home/)).toBeInTheDocument()
    expect(screen.queryByText('catalog page')).not.toBeInTheDocument()
  })

  it('without a session, sends the visitor to /start once the restore finds none', async () => {
    server.use(
      http.post('*/api/v1/auth/refresh', () =>
        HttpResponse.json({ code: 'UNAUTHORIZED' }, { status: 401 }),
      ),
    )
    renderShell(null)
    expect(await screen.findByText('start /admin/catalog')).toBeInTheDocument()
    expect(screen.queryByText('catalog page')).not.toBeInTheDocument()
  })
})
