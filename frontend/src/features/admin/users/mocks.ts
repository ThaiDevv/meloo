import { http, HttpResponse } from 'msw'
import type { components } from '@/shared/api/schema.gen'
import example from '../../../../../contracts/examples/admin/get-admin-user.200.json'
import page from '../../../../../contracts/examples/admin/list-admin-users.200.json'

type User = components['schemas']['AdminUser']
let account: User = {
  ...example,
  kind: 'REGISTERED',
  role: 'USER',
  status: 'ACTIVE',
  linkedProviders: ['GOOGLE'],
}

/** Contract-derived preview data. Writes are in-memory and reset on page reload. */
export const adminUsersHandlers = [
  http.get('*/api/v1/admin/users', ({ request }) => {
    const q = new URL(request.url).searchParams.get('q')?.toLowerCase() ?? ''
    const items = `${account.nickname} ${account.email ?? ''}`.toLowerCase().includes(q)
      ? [account]
      : []
    return HttpResponse.json({ ...page, items, totalItems: items.length, totalPages: items.length })
  }),
  http.get('*/api/v1/admin/users/:id', ({ params }) =>
    params.id === account.id
      ? HttpResponse.json(account)
      : HttpResponse.json({ code: 'USER_NOT_FOUND' }, { status: 404 }),
  ),
  http.put('*/api/v1/admin/users/:id/lock', async ({ request, params }) => {
    const body: unknown = await request.json()
    if (params.id !== account.id)
      return HttpResponse.json({ code: 'USER_NOT_FOUND' }, { status: 404 })
    if (
      !body ||
      typeof body !== 'object' ||
      !('locked' in body) ||
      typeof body.locked !== 'boolean'
    )
      return HttpResponse.json({}, { status: 400 })
    const reason = 'reason' in body && typeof body.reason === 'string' ? body.reason.trim() : ''
    if (body.locked && !reason)
      return HttpResponse.json({ code: 'VALIDATION_FAILED' }, { status: 400 })
    account = {
      ...account,
      status: body.locked ? 'LOCKED' : 'ACTIVE',
      lockReason: body.locked ? reason : null,
      lockedAt: body.locked ? new Date().toISOString() : null,
    }
    return HttpResponse.json(account)
  }),
  http.put('*/api/v1/admin/users/:id/role', async ({ request, params }) => {
    const body: unknown = await request.json()
    if (params.id !== account.id)
      return HttpResponse.json({ code: 'USER_NOT_FOUND' }, { status: 404 })
    if (
      !body ||
      typeof body !== 'object' ||
      !('role' in body) ||
      (body.role !== 'USER' && body.role !== 'ADMIN')
    )
      return HttpResponse.json({}, { status: 400 })
    account = { ...account, role: body.role }
    return HttpResponse.json(account)
  }),
]
