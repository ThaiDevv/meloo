# AGENTS.md — frontend

Luật chung của repo nằm ở `../AGENTS.md`. Quy ước code frontend (cấu trúc feature, state, i18n, a11y, test) nằm ở skill `meloo-frontend` (`.claude/skills/meloo-frontend/`, Codex thấy qua `.agents/skills/`). Đọc skill trước khi code.

## Lệnh (chạy trong `frontend/`, cần Node ≥ 22 và pnpm 10)

| Việc                  | Lệnh                                                                        |
| --------------------- | --------------------------------------------------------------------------- |
| Cài thư viện          | `pnpm install --frozen-lockfile`                                            |
| Chạy dev              | `pnpm dev` (http://localhost:5173, proxy `/api` và `/ws` tới backend :8080) |
| Chạy dev với mock     | `VITE_API_MOCK=true pnpm dev`                                               |
| Kiểm tra kiểu         | `pnpm typecheck`                                                            |
| Lint                  | `pnpm lint` (format: `pnpm format:check`, sửa: `pnpm format`)               |
| Test                  | `pnpm test` (Vitest + Testing Library + MSW)                                |
| Build                 | `pnpm build`                                                                |
| E2E                   | `pnpm e2e` (Playwright; lần đầu: `pnpm exec playwright install chromium`)   |
| Sinh type từ hợp đồng | `pnpm gen:api`                                                              |

Trước khi mở PR: `pnpm typecheck && pnpm lint && pnpm format:check && pnpm test && pnpm build`.
i18n: `python3 ../.claude/skills/meloo-qa-check/scripts/check-i18n-parity.py src/locales`.

## Luật riêng của frontend

- `src/shared/api/schema.gen.ts` do `pnpm gen:api` sinh từ `../contracts/openapi.yaml`; không sửa tay.
- Route khai báo sẵn trong `src/features/<f>/routes.tsx`; ticket sau chỉ thay component Placeholder, không sửa `routes.tsx`, `src/app/**` hay `main.tsx`.
- `package.json` và `pnpm-lock.yaml` là `hot-fe-deps`; không thêm thư viện nếu ticket không giữ label này.
- Mọi chữ hiện ra màn hình đi qua `t('key')` và có đủ ở `src/locales/vi` lẫn `src/locales/en`.
- `src/shared/monitoring/index.ts` do INF-04 điền Sentry; không đổi chữ ký `initMonitoring()`.
