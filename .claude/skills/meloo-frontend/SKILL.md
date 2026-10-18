---
name: meloo-frontend
description: "Quy ước code frontend của Meloo (React + TypeScript strict + Vite, Tailwind + shadcn/ui, TanStack Query, Zustand, i18next, Web Audio, STOMP, MSW, Vitest, Playwright): cấu trúc feature, route dựng sẵn, gọi API qua client sinh từ OpenAPI, state realtime, phát đoạn nhạc chính xác, i18n vi/en, a11y và test. Dùng khi làm hoặc review ticket frontend (FE-*, SET-04, LEG-01) hay khi viết code trong frontend/."
---

# meloo-frontend

Thái không đọc được React. Lưới an toàn vì vậy là: TypeScript strict, test, Playwright, bản preview Vercel và review chéo. Code phải dễ kiểm chứng bằng chính những công cụ đó: kiểu chặt, hành vi có test, giao diện xem được ở trang `/_ui` và có ảnh chụp trong PR.

## Đọc trước khi code
1. `frontend/AGENTS.md`: lệnh build/test (tạo ở SET-04).
2. Hợp đồng liên quan trong `contracts/` và ví dụ trong `contracts/examples/**`.
3. Component có sẵn trong `src/shared/ui` (xem trang `/_ui`).

## Cấu trúc
```
src/
  app/                 # router, provider, layout — hot-fe-app, không sửa nếu không giữ label
  features/<f>/
    routes.tsx         # route của feature, dựng sẵn từ SET-04
    <phần>/            # thư mục riêng của từng ticket, VD solo/setup, solo/play
      components/  hooks/  api/  store/  *.test.tsx
  shared/
    ui/                # design system (FE-01)
    api/               # client openapi-fetch + schema.gen.ts (sinh tự động, không sửa tay)
    realtime/          # STOMP client, hook, messages.ts (type sự kiện từ CT-02)
  locales/{vi,en}/<feature>.json, common.json
```
- Route đã khai báo sẵn từ SET-04 (`references/routes.md`). Ticket chỉ thay component placeholder trong thư mục của mình, không thêm route vào `routes.tsx`. Cần route mới thì ghi "Ticket bổ sung" trong PR.
- Thành phần của feature này hiện trên màn hình của feature khác thì đi qua stub dựng sẵn (bảng "Chỗ gắn dựng sẵn" trong `references/routes.md`): thay nội dung stub, giữ nguyên tên và props.
- Không import vào bên trong feature khác. Thứ cần dùng chung thì đưa vào `shared/` (cần ticket).

## Dữ liệu và state
- REST: hook TanStack Query đặt trong `features/<f>/<phần>/api/`, gọi qua client `src/shared/api` với path có kiểu. Không `fetch` tay, không `any`, không `as` trên dữ liệu từ server.
- Query key dạng mảng, VD `['solo', 'session', id]`. Mutation invalidate đúng key.
- Realtime: STOMP qua `src/shared/realtime`; sự kiện đổ vào store Zustand của feature (cập nhật bất biến); đồng hồ tính theo `serverTime` (`references/realtime.md`).
- Token: access token chỉ giữ trong bộ nhớ. Nhiều request 401 cùng lúc thì chỉ refresh một lần (FE-AUTH-01).
- Mock: MSW handler dùng dữ liệu từ `contracts/examples/**`, bật bằng `VITE_API_MOCK=true` (preview Vercel chạy mock).
- Cờ tính năng: `VITE_FEATURE_<TÊN>` (VD nút đăng nhập Facebook).
- Thư viện đã cài sẵn ở SET-04 (gồm recharts cho biểu đồ, @sentry/react). Cần thư viện khác thì dừng: `package.json` là hot-fe-deps.

## Giao diện
- Dùng component trong `src/shared/ui`; giao diện tối mặc định; responsive từ 360px.
- Màn nào cũng có loading (Skeleton), lỗi (ErrorState + nút thử lại) và rỗng (EmptyState).
- A11y: điều khiển được bằng bàn phím, focus thấy rõ, `aria-label` cho nút chỉ có icon, tương phản đạt WCAG AA.
- i18n: mọi chữ đi qua `t('key')` với namespace là tên feature; thêm key ở cả `vi` và `en`; kiểm bằng `python3 .claude/skills/meloo-qa-check/scripts/check-i18n-parity.py frontend/src/locales`.

## Âm thanh
Màn chơi (FE-GAME-01) và mọi chỗ phát đoạn nhạc: đọc `references/web-audio.md`.

## Test
- Vitest + Testing Library + user-event cho hành vi trong AC; MSW cho API.
- Playwright cho luồng chính. Ticket QA viết E2E đầy đủ; ticket FE có thể thêm smoke test trong thư mục của mình.
- PR frontend kèm ảnh chụp (hoặc video ngắn) các trạng thái chính và link preview Vercel.
