import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useRoutes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import i18n from '@/i18n'
import { routes } from './routes'

function LegalRoutes() {
  return useRoutes(routes)
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <LegalRoutes />
    </MemoryRouter>,
  )
}

describe('legal pages', () => {
  it.each([
    ['vi', 'privacy', 'Chính sách quyền riêng tư'],
    ['vi', 'terms', 'Điều khoản sử dụng'],
    ['vi', 'data-deletion', 'Hướng dẫn xóa dữ liệu'],
    ['en', 'privacy', 'Privacy policy'],
    ['en', 'terms', 'Terms of use'],
    ['en', 'data-deletion', 'Data deletion instructions'],
  ])('renders %s /legal/%s with readable sections and contact', async (language, page, title) => {
    await i18n.changeLanguage(language)
    renderAt(`/legal/${page}`)
    expect(screen.getByRole('heading', { level: 1, name: title })).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 }).length).toBeGreaterThanOrEqual(4)
    expect(screen.getByRole('link', { name: 'tranvanthai20062@gmail.com' })).toHaveAttribute(
      'href',
      'mailto:tranvanthai20062@gmail.com',
    )
    expect(screen.getByRole('article')).not.toHaveTextContent(/LEG-01|legal\./)
  })

  it('links between public pages and updates content when the language changes', async () => {
    const user = userEvent.setup()
    renderAt('/legal/privacy')
    const nav = screen.getByRole('navigation', { name: 'Các trang pháp lý' })
    expect(within(nav).getByRole('link', { name: 'Chính sách quyền riêng tư' })).toHaveAttribute(
      'aria-current',
      'page',
    )
    await user.click(within(nav).getByRole('link', { name: 'Hướng dẫn xóa dữ liệu' }))
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Hướng dẫn xóa dữ liệu')
    await act(() => i18n.changeLanguage('en'))
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Data deletion instructions',
    )
    expect(
      screen.getByText(/Self-service account deletion is not available yet/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/We process requests within 30 days and reply by email/),
    ).toBeInTheDocument()
  })

  it.each(['vi', 'en'])(
    'discloses retention and pending cleanup honestly in %s',
    async (language) => {
      await i18n.changeLanguage(language)
      renderAt('/legal/privacy')
      const retention = screen.getByText(
        language === 'vi' ? /Theo cam kết của Meloo/ : /Meloo commits to deleting guest data/,
      )
      expect(retention).toHaveTextContent(
        language === 'vi' ? '30 ngày không hoạt động' : '30 days of inactivity',
      )
      expect(retention).toHaveTextContent(
        language === 'vi' ? 'đang chờ triển khai' : 'awaiting implementation',
      )
      expect(retention).toHaveTextContent(
        language === 'vi' ? 'không lưu file nhạc' : 'do not store music files',
      )
    },
  )
})
