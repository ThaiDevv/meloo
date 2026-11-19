import { http, HttpResponse } from 'msw'
import example from '../../../../../contracts/examples/admin/list-admin-audit-logs.200.json'

/** Static audit fixture for preview; real audit entries are written exclusively by the backend. */
export const adminAuditHandlers = [
  http.get('*/api/v1/admin/audit-logs', ({ request }) => {
    const query = new URL(request.url).searchParams
    const items = example.items.filter(
      (item) =>
        (!query.get('actorId') || query.get('actorId') === item.actorId) &&
        (!query.get('action') || query.get('action') === item.action) &&
        (!query.get('targetType') || query.get('targetType') === item.targetType) &&
        (!query.get('from') || Date.parse(item.createdAt) >= Date.parse(query.get('from') ?? '')) &&
        (!query.get('to') || Date.parse(item.createdAt) <= Date.parse(query.get('to') ?? '')),
    )
    return HttpResponse.json({
      ...example,
      items,
      totalItems: items.length,
      totalPages: items.length,
    })
  }),
]
