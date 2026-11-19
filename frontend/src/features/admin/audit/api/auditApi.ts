import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { paths } from '@/shared/api/schema.gen'
import { AdminApiError } from '../../shell/adminApi'

type Filters = NonNullable<paths['/api/v1/admin/audit-logs']['get']['parameters']['query']>
export function useAudit(filters: Filters) {
  return useQuery({
    queryKey: ['admin', 'audit', filters],
    queryFn: async ({ signal }) => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/audit-logs', {
        params: { query: { size: 20, ...filters } },
        signal,
      })
      if (!data) throw new AdminApiError('listAdminAuditLogs', response.status, error)
      return data
    },
  })
}
