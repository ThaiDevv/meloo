import { http, HttpResponse } from 'msw'
import analyticsExample from '../../../../../contracts/examples/admin/get-admin-analytics.200.json'
import dashboardExample from '../../../../../contracts/examples/admin/get-admin-dashboard.200.json'

/** Dashboard mocks from contracts/examples/admin. */
export const adminDashboardHandlers = [
  http.get('*/api/v1/admin/dashboard', () => HttpResponse.json(dashboardExample)),
  http.get('*/api/v1/admin/analytics', () => HttpResponse.json(analyticsExample)),
]
