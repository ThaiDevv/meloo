# Route khai báo sẵn ở SET-04

Đây là bản đề xuất; SET-04 chốt danh sách cuối cùng. Mỗi route trỏ tới một component `Placeholder` đặt trong đúng thư mục của ticket sẽ làm nó. Ticket sau chỉ sửa component đó, không sửa `routes.tsx`.

| Route | Thư mục | Ticket thay placeholder |
|---|---|---|
| `/` | `features/home` | FE-SOLO-01 |
| `/start` (đặt biệt danh) | `features/auth/guest` | FE-AUTH-01 |
| `/auth/callback` | `features/auth/oauth` | FE-AUTH-02 |
| `/solo` | `features/solo/setup` | FE-SOLO-01 |
| `/solo/:matchId` | `features/solo/play` | FE-SOLO-02 |
| `/daily` | `features/daily` | FE-DAILY-01 |
| `/rooms/new` | `features/room/lobby` | FE-ROOM-01 |
| `/r/:code` (sảnh chờ) | `features/room/lobby` | FE-ROOM-01 |
| `/r/:code/play` (trận) | `features/room/match` | FE-ROOM-02 |
| `/rooms` (phòng công khai), `/quick-match` | `features/room/public` | FE-ROOM-03 |
| `/profile`, `/profile/matches/:matchId` | `features/profile` | FE-PROF-01 |
| `/leaderboard` | `features/leaderboard` | FE-LB-01 |
| `/admin`, `/admin/catalog`, `/admin/playlists` | `features/admin/{dashboard,catalog,playlists}` | FE-ADM-01 |
| `/admin/users`, `/admin/moderation`, `/admin/audit` | `features/admin/{users,moderation,audit}` | FE-ADM-02 |
| `/legal/privacy`, `/legal/terms`, `/legal/data-deletion` | `features/legal` | LEG-01 |
| `/_ui` (chỉ ở dev và preview) | `shared/ui` | FE-01 |

Guard: `/admin/**` là layout route, element là `features/admin/shell/AdminShell` (SET-04 dựng placeholder có sẵn guard role ADMIN; FE-ADM-01 hoàn thiện layout, menu và guard trong `features/admin/shell`, FE-ADM-02 dùng lại). `/profile/**` yêu cầu đăng nhập hoặc là khách.

## Chỗ gắn dựng sẵn
Màn hình của một feature đôi khi phải hiện thành phần của feature khác (khung chat trong sảnh, menu tài khoản trên header). Để ticket sau không phải sửa thư mục của ticket trước, ticket đến trước tạo sẵn một stub export đúng tên và gắn nó vào; ticket sau chỉ thay nội dung stub, giữ nguyên tên và props.

| Stub | Ai tạo và gắn | Ai thay bản thật |
|---|---|---|
| `features/auth/AccountMenu.tsx` (header) | FE-01 | FE-AUTH-02 |
| `features/chat/index.ts` → `RoomChat` (sảnh, màn trận) | FE-ROOM-01 gắn ở sảnh, FE-ROOM-02 gắn ở màn trận | FE-CHAT-01 |
| `shared/monitoring/index.ts` → `initMonitoring()` (gọi trong `main.tsx`) | SET-04 | INF-04 |

Gắn vào màn hình đã có của feature khác (lời mời đăng nhập, toast huy hiệu, thanh voice) chỉ làm khi ticket ghi rõ thư mục đó trong Phạm vi file kèm chú thích "chỉ để gắn". Diff ở thư mục đó chỉ được là phần gắn.
