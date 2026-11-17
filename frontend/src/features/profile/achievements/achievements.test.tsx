import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, renderHook, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import i18n from '@/i18n'
import { server } from '@/mocks/server'
import { useAuthStore } from '@/shared/api/authStore'
import { useToastStore } from '@/shared/ui/toast-store'
import example from '../../../../../contracts/examples/progression/list-my-achievements.200.json'
import guest from '../../../../../contracts/examples/identity/create-guest.201.json'
import { AchievementsGrid } from './AchievementsGrid'
import { markSeen, readSeen } from './seen'
import { ACHIEVEMENTS_RECHECK_MS, useAchievementNotifications } from './useAchievementNotifications'

let sequence = 0
let userId: string
let calls = 0
let response = structuredClone(example)
let client: QueryClient
const timers = vi.spyOn(globalThis, 'setTimeout')
function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
function unlock() {
  response = {
    ...response,
    unlockedCount: 4,
    items: response.items.map((item) =>
      item.code === 'DAILY_STREAK_7'
        ? { ...item, unlocked: true, unlockedAt: '2026-11-17T12:00:00Z' }
        : item,
    ),
  }
}
function retryOnce() {
  const entry = timers.mock.calls.filter(([, ms]) => ms === ACHIEVEMENTS_RECHECK_MS).at(-1)
  if (!entry || typeof entry[0] !== 'function') throw new Error('no achievement recheck scheduled')
  const callback = entry[0]
  act(() => {
    callback()
  })
}
beforeEach(() => {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  userId = `achievements-test-${String(++sequence)}`
  useAuthStore.setState({
    user: { ...guest.user, kind: 'GUEST', role: 'USER', avatarId: 'headphones', id: userId },
  })
  useToastStore.setState({ toasts: [] })
  calls = 0
  response = structuredClone(example)
  timers.mockClear()
  server.use(
    http.get('*/api/v1/me/achievements', () => {
      calls++
      return HttpResponse.json(response)
    }),
  )
})
afterEach(() => {
  useAuthStore.getState().clear()
  client.clear()
})

describe('AchievementsGrid', () => {
  it('shows locked and unlocked badges, conditions, dates and server ordering in vi/en', async () => {
    response.items.reverse()
    render(<AchievementsGrid />, { wrapper })
    expect(screen.getByText('Đang tải…')).toBeInTheDocument()
    expect(await screen.findByText('Chiến thắng đầu tay')).toBeInTheDocument()
    expect(screen.getAllByText('Đã mở khóa')).toHaveLength(3)
    expect(screen.getByText('Chưa mở khóa')).toBeInTheDocument()
    expect(
      screen.getByText('Giữ chuỗi thắng Thử thách mỗi ngày liên tiếp 7 ngày.'),
    ).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Chiến thắng đầu tay')
    expect(screen.getByText('Đã mở 3 / 8 huy hiệu')).toBeInTheDocument()
    await act(() => i18n.changeLanguage('en'))
    expect(screen.getByText('7-Day Daily Streak')).toBeInTheDocument()
    expect(
      screen.getByText('Maintain a 7-day winning streak in the Daily Challenge.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Locked')).toBeInTheDocument()
    expect(readSeen(userId)?.get('FIRST_WIN')).toBe(Date.parse('2026-10-21T10:12:00Z'))
  })
  it('recovers from an API error with Retry and supports an empty catalog', async () => {
    server.use(http.get('*/api/v1/me/achievements', () => HttpResponse.json({}, { status: 500 })))
    render(<AchievementsGrid />, { wrapper })
    expect(await screen.findByRole('alert')).toHaveTextContent('Không tải được huy hiệu')
    server.use(
      http.get('*/api/v1/me/achievements', () =>
        HttpResponse.json({ items: [], unlockedCount: 0, totalCount: 0 }),
      ),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có huy hiệu trong bộ sưu tập.')).toBeInTheDocument()
  })
})
describe('achievement notifications', () => {
  it('captures a quiet baseline, announces on summary and deduplicates on remount', async () => {
    const hook = renderHook(
      ({ finished }) => {
        useAchievementNotifications(finished)
      },
      {
        initialProps: { finished: false },
        wrapper,
      },
    )
    await waitFor(() => {
      expect(readSeen(userId)?.size).toBe(3)
    })
    expect(useToastStore.getState().toasts).toHaveLength(0)
    unlock()
    hook.rerender({ finished: true })
    await waitFor(() => {
      expect(useToastStore.getState().toasts).toHaveLength(1)
    })
    expect(useToastStore.getState().toasts[0]).toMatchObject({
      title: 'Mở khóa huy hiệu',
      description: '7 ngày bền bỉ',
    })
    expect(timers.mock.calls.filter(([, ms]) => ms === ACHIEVEMENTS_RECHECK_MS)).toHaveLength(0)
    hook.unmount()
    renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    await waitFor(() => {
      expect(calls).toBe(3)
    })
    expect(useToastStore.getState().toasts).toHaveLength(1)
  })
  it('rechecks exactly once for async evaluation, including badges with equal timestamps', async () => {
    markSeen(userId, example.items)
    renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    await waitFor(() => {
      expect(timers.mock.calls.some(([, ms]) => ms === ACHIEVEMENTS_RECHECK_MS)).toBe(true)
    })
    unlock()
    response.items = response.items.map((item) =>
      item.code === 'DAILY_STREAK_7'
        ? { ...item, unlockedAt: example.items[0]?.unlockedAt ?? '' }
        : item,
    )
    retryOnce()
    await waitFor(() => {
      expect(useToastStore.getState().toasts).toHaveLength(1)
    })
    expect(calls).toBe(2)
    expect(timers.mock.calls.filter(([, ms]) => ms === ACHIEVEMENTS_RECHECK_MS)).toHaveLength(1)
  })
  it('does not announce historical badges on the first direct visit to a summary', async () => {
    renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    await waitFor(() => {
      expect(readSeen(userId)?.size).toBe(3)
    })
    retryOnce()
    await waitFor(() => {
      expect(calls).toBe(2)
    })
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })
  it('retries an optional failure once and clears the timer on unmount', async () => {
    server.use(
      http.get('*/api/v1/me/achievements', () => {
        calls++
        return HttpResponse.json({}, { status: 503 })
      }),
    )
    const clear = vi.spyOn(globalThis, 'clearTimeout')
    const hook = renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    await waitFor(() => {
      expect(timers.mock.calls.some(([, ms]) => ms === ACHIEVEMENTS_RECHECK_MS)).toBe(true)
    })
    retryOnce()
    await waitFor(() => {
      expect(calls).toBe(2)
    })
    expect(useToastStore.getState().toasts).toHaveLength(0)
    hook.unmount()
    expect(clear).toHaveBeenCalled()
  })
  it('ignores a delayed response from an account after signing out', async () => {
    markSeen(userId, [])
    server.use(
      http.get('*/api/v1/me/achievements', async () => {
        await delay(60)
        return HttpResponse.json(response)
      }),
    )
    renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    act(() => {
      useAuthStore.getState().clear()
    })
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })
  it('separates identities, translates English toasts and works when storage is unavailable', async () => {
    markSeen('another-account', example.items)
    await i18n.changeLanguage('en')
    const storage = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage unavailable')
    })
    markSeen(userId, [])
    const hook = renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    await waitFor(() => {
      expect(useToastStore.getState().toasts).toHaveLength(3)
    })
    expect(useToastStore.getState().toasts[0]?.title).toBe('Achievement unlocked')
    hook.unmount()
    renderHook(
      () => {
        useAchievementNotifications(true)
      },
      { wrapper },
    )
    await waitFor(() => {
      expect(calls).toBe(2)
    })
    expect(useToastStore.getState().toasts).toHaveLength(3)
    storage.mockRestore()
  })
})
