/** Menu of every admin page; FE-ADM-02 fills users, moderation and audit. */
export const ADMIN_MENU = [
  { to: '/admin', key: 'dashboard', end: true },
  { to: '/admin/catalog', key: 'catalog', end: false },
  { to: '/admin/playlists', key: 'playlists', end: false },
  { to: '/admin/users', key: 'users', end: false },
  { to: '/admin/moderation', key: 'moderation', end: false },
  { to: '/admin/audit', key: 'audit', end: false },
] as const
