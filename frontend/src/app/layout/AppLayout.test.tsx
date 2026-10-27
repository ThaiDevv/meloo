import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AccountMenu } from '@/features/auth/AccountMenu'
import { AppLayout } from './AppLayout'

function renderLayout() {
  render(
    <MemoryRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<h1>Nội dung trang</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppLayout', () => {
  it('has a header with the logo, language switcher and theme toggle', () => {
    renderLayout()
    const header = screen.getByRole('banner')
    expect(within(header).getByRole('link', { name: 'Meloo' })).toHaveAttribute('href', '/')
    expect(within(header).getByRole('group', { name: 'Ngôn ngữ' })).toBeInTheDocument()
    expect(
      within(header).getByRole('button', { name: 'Chuyển sang giao diện sáng' }),
    ).toBeInTheDocument()
  })

  it('has a footer linking to the privacy policy and the terms', () => {
    renderLayout()
    const footer = screen.getByRole('contentinfo')
    expect(within(footer).getByRole('link', { name: 'Chính sách quyền riêng tư' })).toHaveAttribute(
      'href',
      '/legal/privacy',
    )
    expect(within(footer).getByRole('link', { name: 'Điều khoản' })).toHaveAttribute(
      'href',
      '/legal/terms',
    )
  })

  it('renders the page inside <main> and offers a skip link as the first stop of the keyboard', async () => {
    renderLayout()
    expect(
      within(screen.getByRole('main')).getByRole('heading', { name: 'Nội dung trang' }),
    ).toBeInTheDocument()

    await userEvent.tab()
    const skip = screen.getByRole('link', { name: 'Bỏ qua, tới nội dung chính' })
    expect(skip).toHaveFocus()
    expect(skip).toHaveAttribute('href', '#main')
  })

  it('reaches every header control by keyboard', async () => {
    renderLayout()
    await userEvent.tab() // skip link
    await userEvent.tab() // logo
    expect(screen.getByRole('link', { name: 'Meloo' })).toHaveFocus()
    await userEvent.tab() // VI
    await userEvent.tab() // EN
    await userEvent.tab() // theme toggle
    expect(screen.getByRole('button', { name: /giao diện/ })).toHaveFocus()
  })
})

describe('AccountMenu stub', () => {
  it('renders nothing until FE-AUTH-02 replaces it', () => {
    const { container } = render(<AccountMenu />)
    expect(container).toBeEmptyDOMElement()
  })
})
