import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '@/shared/api/client'
import type { components, paths } from '@/shared/api/schema.gen'
import { AdminApiError } from '../../shell/adminApi'

export type AdminUser = components['schemas']['AdminUser']
// MEL-87 returns 501 for the optional kind/role/status filters; expose only supported search.
type Filters = Pick<
  NonNullable<paths['/api/v1/admin/users']['get']['parameters']['query']>,
  'q' | 'page' | 'size'
>
export function useAdminUsers(filters: Filters) {
  return useQuery({
    queryKey: ['admin', 'users', filters],
    queryFn: async ({ signal }) => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/users', {
        params: { query: { size: 20, ...filters } },
        signal,
      })
      if (!data) throw new AdminApiError('listAdminUsers', response.status, error)
      return data
    },
  })
}
export function useAdminUser(id: string) {
  return useQuery({
    queryKey: ['admin', 'user', id],
    queryFn: async ({ signal }) => {
      const { data, error, response } = await apiClient.GET('/api/v1/admin/users/{userId}', {
        params: { path: { userId: id } },
        signal,
      })
      if (!data) throw new AdminApiError('getAdminUser', response.status, error)
      return data
    },
  })
}
type Change = { id: string } & (
  | { type: 'lock'; body: components['schemas']['AdminUserLockRequest'] }
  | { type: 'role'; body: components['schemas']['AdminUserRoleRequest'] }
)
export function useChangeUser() {
  const cache = useQueryClient()
  return useMutation({
    mutationFn: async (change: Change) => {
      const options = { params: { path: { userId: change.id } } }
      const result =
        change.type === 'lock'
          ? await apiClient.PUT('/api/v1/admin/users/{userId}/lock', {
              ...options,
              body: change.body,
            })
          : await apiClient.PUT('/api/v1/admin/users/{userId}/role', {
              ...options,
              body: change.body,
            })
      if (!result.data)
        throw new AdminApiError('changeAdminUser', result.response.status, result.error)
      return result.data
    },
    onSuccess: (user) => {
      cache.setQueryData(['admin', 'user', user.id], user)
      void cache.invalidateQueries({ queryKey: ['admin', 'users'] })
      void cache.invalidateQueries({ queryKey: ['admin', 'audit'] })
      void cache.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    },
  })
}
