import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { renderAdmin } from '../shell/testing'
import { UsersPage } from '../users/UsersPage'
import { AuditPage } from '../audit/AuditPage'
import { ModerationPage } from './ModerationPage'

// Uses the actual shared MSW registry: no test-specific server.use overrides.
it('preview registry serves users and updates lock status in memory', async () => {
  renderAdmin(<UsersPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem MinhNhac' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Khóa tài khoản' }))
  await userEvent.type(screen.getByLabelText('Lý do khóa'), 'Preview spam')
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
  expect(await screen.findByRole('button', { name: 'Mở khóa tài khoản' })).toBeVisible()
})
it('preview registry serves reports, persists words and supplies the daily schedule', async () => {
  renderAdmin(<ModerationPage />)
  expect(await screen.findByText('Ngữ cảnh (2 tin nhắn)')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Từ cấm' }))
  expect(await screen.findByText('khốn nạn')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Thêm từ cấm' }))
  await userEvent.type(screen.getByLabelText('Từ hoặc cụm từ'), 'preview-word')
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  expect(await screen.findByText('preview-word')).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Lịch thử thách' }))
  expect(await screen.findByText('See Tình · Hoàng Thùy Linh')).toBeVisible()
})
it('preview registry filters the static audit fixture', async () => {
  renderAdmin(<AuditPage />)
  expect(await screen.findByText('USER_LOCK')).toBeVisible()
  await userEvent.type(screen.getByLabelText('Mã thao tác'), 'NO_SUCH_ACTION')
  await userEvent.click(screen.getByRole('button', { name: 'Lọc' }))
  expect(await screen.findByText('Không có kết quả')).toBeVisible()
})
