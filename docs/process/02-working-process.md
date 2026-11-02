# PM-09 · Quy trình làm việc nhóm (CLO3 / Rubric A5.3)

> Ticket: MEL-115 · Trạng thái: **Sẵn sàng review** (Antigravity thực hiện theo phân công của Thái) · Ngày: 02/11/2026  
> Chuẩn đầu ra môn học: **CLO3** ("Xây dựng và vận hành quy trình hoạt động nhóm hiệu quả, chuyên nghiệp theo tiêu chuẩn A5.3").  
> Tài liệu liên quan: Kế hoạch dự án [`docs/process/01-project-plan.md`](file:///home/thaidevvv/orca/workspaces/Meloo/MEL-115/docs/process/01-project-plan.md), Hướng dẫn Harness [`docs/process/harness.md`](file:///home/thaidevvv/orca/workspaces/Meloo/MEL-115/docs/process/harness.md), Luật chung [`AGENTS.md`](file:///home/thaidevvv/orca/workspaces/Meloo/MEL-115/AGENTS.md).

---

## Tổng quan mô hình làm việc

Dự án Meloo áp dụng mô hình cộng tác đặc biệt: **Một người (Thái - Product Owner & Tech Lead) chỉ đạo và điều phối một nhóm các AI Coding Agent chuyên trách (Codex, Claude, Antigravity) trong môi trường phát triển đa tác tử Orca.**

Mô hình này không phải là "nhờ AI viết hộ code", mà là **xây dựng một quy trình kỹ thuật phần mềm có kiểm soát chặt chẽ (Supervised AI Agent Pipeline)**: mọi hành vi của agent đều bị đóng khung bởi các ràng buộc kiến trúc, kiểm tra hợp đồng API tự động, ma trận quyền hạn và cổng chất lượng trước khi được tích hợp vào nhánh chính.

---

## 1. Phân công công việc (Task Allocation & Role Division)

### 1.1. Ma trận trách nhiệm (RACI Matrix)

| Vai trò | Thành viên | Trách nhiệm chính (Làm gì) | Trách nhiệm phụ (Review / Kiểm tra) | Quyền hạn đặc biệt |
|---|---|---|---|---|
| **Tech Lead / PO** | **Trần Văn Thái** | - Định hướng sản phẩm, yêu cầu nghiệp vụ BR-01…BR-29.<br>- Lập kế hoạch Sprint, điều phối Jira.<br>- Duyệt kiến trúc và ra quyết định kỹ thuật cuối cùng. | - Nghiệm thu cuối cùng mọi Pull Request.<br>- Kiểm tra tính toàn vẹn của đồ án và rubric. | **Người duy nhất** được quyền bấm Merge PR, đổi trạng thái Jira và tạo branch chính thức. |
| **Backend Agent** | **Codex** | - Lập trình Backend (Java 21, Spring Boot 4, Spring Modulith).<br>- Quản lý CSDL (PostgreSQL 17, Flyway migration).<br>- Hạ tầng CI/CD, Docker, Caddy, AWS EC2 và test tải k6. | - Review chéo các PR do Claude hoặc Antigravity thực hiện liên quan đến backend, API hoặc tài liệu. | Đọc mã nguồn, chạy lệnh trong worktree được giao. Không được merge hay sửa Jira. |
| **Frontend Agent** | **Claude** | - Lập trình Frontend (React, TypeScript strict, Vite, Tailwind, Zustand).<br>- Thiết kế hợp đồng API-first (`openapi.yaml`, `asyncapi.yaml`).<br>- Soạn thảo tài liệu học thuật (SRS, UML PlantUML, ADR). | - Review chéo các PR backend hoặc hạ tầng do Codex thực hiện. | Đọc mã nguồn, chạy build frontend, tạo PR. Không được merge hay sửa Jira. |
| **Versatile Agent** | **Antigravity** | - Thực hiện các ticket theo phân công trực tiếp của Thái (khi cần linh hoạt xử lý backend, frontend, hợp đồng hoặc tài liệu). | - Tham gia review chéo độc lập khi được chỉ định. | Làm việc trong worktree riêng; ghi nhận danh tính tác giả thực tế trên PR và comment. |
| **Subagents** | **`meloo-qa`, `meloo-reviewer`, `meloo-verifier`** | - Chạy ngầm trong phiên làm việc để kiểm tra khớp nối (integration coherence) và thẩm định phản biện lỗi một cách khách quan. | - Đóng vai trò "con mắt mới" (fresh eyes), phát hiện lỗi trước khi mở PR. | Chạy sandbox read-only, gửi báo cáo về cho phiên chính. |

### 1.2. Phân chia lane chuyên môn hóa (Specialized Lanes)
Mỗi ticket Jira được gán nhãn (`labels`) chỉ định lane chuyên môn và skill tương ứng:
- `meloo-backend` (`be`, `db`): Cấu trúc module, giao dịch, chống N+1, hàng đợi phòng, Testcontainers.
- `meloo-frontend` (`fe`): Component UI, audio playback, client sinh tự động, i18n vi/en, a11y.
- `meloo-contract` (`contract`): OpenAPI 3.1, AsyncAPI 3, ví dụ JSON mẫu, lint hợp đồng.
- `meloo-infra` (`infra`, `hot-ci`): GitHub Actions, Docker, Caddy, AWS script.
- `meloo-testing` (`qa`): E2E Playwright, k6 load testing, test plan.
- `meloo-docs` (`doc`, `pm`, `biz`): SRS, use case, PlantUML, ADR, báo cáo.

*Vì sao làm vậy:* Việc phân định rạch ròi vai trò (người viết khác người review, luôn luôn) loại bỏ thiên kiến xác nhận (confirmation bias) của AI. Mô hình lane giúp nạp đúng ngữ cảnh và công cụ cần thiết, tiết kiệm token và tránh hiện tượng agent làm sai thẩm quyền.

---

## 2. Quản lý và kiểm soát (Management & Control Mechanisms)

### 2.1. Quản lý tiến độ qua Jira Board
Toàn bộ dự án được quản trị tập trung trên Jira Software (`site: tranvanthai20062-1779090911683.atlassian.net`) với bảng Kanban/Scrum kết hợp. Quy trình luân chuyển trạng thái ticket:
```
[To Do] ──> [In Progress] ──> [In Review] ──> [Done]
```
- **Nguyên tắc bảo vệ:** Các AI agent chỉ có quyền đọc Jira qua MCP server chỉ đọc (`atlassian-mcp-server`), tuyệt đối không có quyền ghi, sửa hay chuyển trạng thái Jira. Chỉ Thái mới có quyền kéo ticket trên bảng.

### 2.2. Cổng kiểm soát Definition of Ready (DoR - Cổng chặn đầu vào)
Một ticket chỉ được phép chuyển sang `In Progress` và bắt đầu viết code khi thỏa mãn đồng thời **5 tiêu chí DoR cứng** (xác minh tự động qua `meloo-ticket`):
1. **Mục tiêu & AC rõ ràng:** Có mục tiêu cụ thể và Acceptance Criteria kiểm chứng được bằng test.
2. **Phạm vi file xác định:** Có danh sách glob file được phép sửa (`Phạm vi file`), không mở rộng tùy tiện.
3. **Phụ thuộc đã merge vào `main`:** Kiểm tra tự động bằng lệnh `deps-merged.sh`, bảo đảm code nền tảng đã có trên nhánh chính.
4. **Không xung đột khóa (Locking Check):** Không trùng thư mục với ticket đang chạy; không trùng nhãn file nóng (`hot-*`).
5. **Vừa sức trong một phiên:** Khối lượng thay đổi ước tính ≤ 1 ngày công và PR ≤ ~400 dòng diff.

*Nếu thiếu bất kỳ tiêu chí nào, agent bắt buộc phải DỪNG LẠI và báo cáo cho Thái, không được tự ý "làm tạm".*

### 2.3. Cổng kiểm soát Definition of Done (DoD - Cổng nghiệm thu đầu ra)
Một Pull Request chỉ được Thái chấp thuận merge khi đạt trọn vẹn các điều kiện:
- **AC Evidence:** 100% Acceptance Criteria có bằng chứng kiểm thử tương ứng (unit, integration hoặc E2E).
- **Test Coverage:** Độ bao phủ kiểm thử (domain logic) đạt ≥ 80%; có Testcontainers cho API/DB.
- **CI Status:** GitHub Actions xanh hoàn toàn: build Java 21, format Spotless, lint ESLint, TypeScript check, kiến trúc Spring Modulith, bảo mật Gitleaks.
- **Phạm vi nghiêm ngặt:** `check-scope.sh` xác nhận không có bất kỳ file nào ngoài phạm vi bị chỉnh sửa.
- **Review chéo:** Agent đối ứng đã review và không còn bất kỳ nhận xét nào ở mức `Blocker` hoặc `Major`.
- **Đa ngôn ngữ & Không nợ kỹ thuật:** Đầy đủ chuỗi i18n tiếng Việt và tiếng Anh; không còn mã `TODO`, `console.log` hay khóa bí mật (secret).

### 2.4. Đánh giá định kỳ Sprint Review & Retrospective
Cuối mỗi sprint, Thái tiến hành đánh giá lại vận tốc (velocity), rà soát các vấn đề kỹ thuật phát sinh và cải tiến harness agent (ghi nhận qua các ticket `PM-02` đến `PM-07`).

*Vì sao làm vậy:* DoR ngăn chặn việc bắt đầu khi yêu cầu còn mù mờ; DoD bảo đảm chất lượng thành phẩm đồng nhất; CI tự động thay thế sự giám sát thủ công lặp đi lặp lại.

---

## 3. Giải quyết vấn đề và ra quyết định (Problem Solving & Decision Making)

### 3.1. Thẩm quyền quyết định tối cao
- **Trần Văn Thái** giữ vai trò Product Owner và Tech Lead. Mọi thay đổi về kiến trúc, bổ sung thư viện bên ngoài, cập nhật quy tắc nghiệp vụ BR-01…BR-29 hoặc giải quyết bất đồng kỹ thuật đều do Thái quyết định. Agent chỉ có vai trò đề xuất, phân tích ưu/nhược điểm và thực thi.

### 3.2. Quản lý quyết định kiến trúc qua ADR (Architecture Decision Records)
Mọi quyết định kỹ thuật nền tảng đều được tài liệu hóa thành các văn bản ADR trong `docs/architecture/adr/` theo cấu trúc: Bối cảnh → Các phương án so sánh → Quyết định chọn → Hệ quả.
- *Ví dụ:* ADR-001 (Monolith module thay vì Microservices), ADR-003 (Nguồn nhạc Deezer + token mờ HMAC), ADR-008 (Hàng đợi lệnh tuần tự phòng chơi qua ConcurrentLinkedQueue).

### 3.3. Cơ chế "Ticket bổ sung" (Supplementary Tickets) xử lý phát sinh thực tế
Khi một agent phát hiện khiếm khuyết thiết kế hoặc thiếu sót kỹ thuật trong quá trình làm việc, agent **không được tự ý sửa lan man ngoài phạm vi ticket hiện tại**. Thay vào đó, quy trình quy định:
1. Agent ghi nhận vấn đề vào báo cáo hoặc `progress.md`.
2. Đề xuất Thái khởi tạo một "Ticket bổ sung" trên Jira để xử lý độc lập.
3. *Minh chứng thực tế trong dự án Meloo:*
   - **MEL-122 (INF-06):** Khắc phục lỗi cấu hình pnpm setup trên môi trường CI.
   - **MEL-123 (BE-GEN-01):** Bổ sung cấu hình Jackson cho code sinh từ OpenAPI.
   - **MEL-124 (CT-01b):** Bổ sung hợp đồng phát audio token mờ khi phát hiện nguy cơ lộ đáp án.
   - **MEL-127 (INF):** Cấu hình Caddy reverse proxy ghi đè `X-Forwarded-For` bảo mật cổng backend.
   - **MEL-146 (BE-GEN-02):** Loại bỏ trường null trong response JSON theo đúng chuẩn hợp đồng.
   - **MEL-149..152:** Bộ 4 ticket bổ sung đồng bộ luật xếp hạng mới (Rank = Matchmaking 2–5 người) trên cả docs, contract, backend và frontend.

*Vì sao làm vậy:* Việc tách thành ticket bổ sung bảo đảm tính minh bạch của lịch sử Git, tránh "nhiễm bẩn" phạm vi (scope creep) của PR đang làm và giúp giảng viên thấy rõ quá trình phản xạ kỹ thuật thực tế của nhóm.

---

## 4. Thu thập và xử lý thông tin (Information Gathering & Processing)

Quy trình thu thập và xử lý thông tin trong Meloo vận hành trên 4 nguồn sự thật:

```
┌──────────────────┐      ┌──────────────────┐
│   Jira Backlog   │      │   Khảo sát thật  │
│ (150 tickets MEL)│      │(100+ mẫu BIZ-03) │
└────────┬─────────┘      └────────┬─────────┘
         │                         │
         ▼                         ▼
┌────────────────────────────────────────────┐
│      Hệ thống thông tin dự án Meloo        │
│   (SRS, OpenAPI, Git Worktree, Progress)   │
└────────┬─────────────────────────┬─────────┘
         │                         │
         ▼                         ▼
┌──────────────────┐      ┌──────────────────┐
│    Git Commits   │      │  Artifacts / PR  │
│ (Conventional)   │      │  (Bằng chứng AC) │
└──────────────────┘      └──────────────────┘
```

1. **Thông tin yêu cầu từ Jira:** Toàn bộ đặc tả, tiêu chí nghiệm thu và liên kết phụ thuộc được truy vấn trực tiếp qua Jira MCP bằng các lệnh chỉ đọc (`getJiraIssue`, `searchJiraIssuesUsingJql`).
2. **Thông tin người dùng định lượng (Khảo sát thực tế):** Dữ liệu không được giả định. Tài liệu nghiệp vụ BIZ-02 và BIZ-04 dựa trên 100+ phản hồi khảo sát Google Forms thật thu thập từ học sinh, sinh viên và nhân viên văn phòng (`docs/business/03-survey.md`).
3. **Thông tin trạng thái phiên làm việc (`progress.md`):** Trong mỗi worktree, thư mục tạm `_workspace/MEL-<số>/` lưu trữ các file `00_ticket.md`, `scope.txt`, `01_plan.md`, `progress.md`. Khi một phiên làm việc bị gián đoạn hoặc chuyển giao, agent kế tiếp chỉ cần đọc các file này để tiếp tục chính xác công việc còn dang dở.
4. **Lịch sử mã nguồn và bằng chứng nghiệm thu:** Lịch sử Git tuân thủ nghiêm ngặt chuẩn *Conventional Commits*. Mọi PR đều chứa bảng ma trận bằng chứng nghiệm thu (Test Evidence Matrix) đối chiếu từng dòng mã với từng tiêu chí AC.

*Vì sao làm vậy:* Thông tin rõ ràng, minh bạch giúp loại bỏ giả định chủ quan, hỗ trợ việc bàn giao mượt mà giữa các agent và cung cấp số liệu thực chứng khi viết báo cáo cuối kỳ.

---

## 5. Phối hợp và cam kết (Coordination & Commitments)

Để bảo đảm nhiều agent và con người có thể làm việc song song mà không dẫm chân lên nhau, dự án áp dụng hệ thống cam kết kỹ thuật chặt chẽ:

### 5.1. Không gian làm việc cô lập qua Git Worktree
- **Nguyên tắc vàng:** *Một ticket = Một worktree = Một branch = Một Pull Request.*
- Mỗi phiên làm việc của agent diễn ra trong một thư mục worktree hoàn toàn riêng biệt do Orca quản lý. Agent không nhìn thấy và không thể can thiệp vào không gian làm việc của agent khác, loại bỏ hoàn toàn nguy cơ ghi đè mã nguồn chưa commit.

### 5.2. Cơ chế khóa tài nguyên chống xung đột (Resource Locking)
- **Khóa thư mục module:** Hai ticket không bao giờ được chạy đồng thời nếu phạm vi file chạm chung một thư mục chức năng (ví dụ: không thể cùng lúc sửa `room/lobby`).
- **Khóa nhãn file nóng (`hot-*`):** Các file có tần suất chỉnh sửa cao được bảo vệ bằng các nhãn khóa đặc biệt trên Jira. Tại một thời điểm, chỉ duy nhất **một ticket In Progress** được phép nắm giữ mỗi nhãn:
  - `hot-contract`: `contracts/openapi.yaml`, `contracts/asyncapi.yaml`
  - `hot-migration`: `backend/src/main/resources/db/migration/**`
  - `hot-pom`: `backend/pom.xml`
  - `hot-fe-deps`: `frontend/package.json`, `pnpm-lock.yaml`
  - `hot-fe-app`: `frontend/src/app/**`
  - `hot-shared`: Cấu hình chung backend và module shared
  - `hot-ci`: Toàn bộ thư mục `.github/workflows/**`

### 5.3. Giới hạn đồng thời (Concurrency Throttling)
- Dự án áp dụng quy tắc: **Tối đa 2 agent làm việc đồng thời** trong toàn bộ hệ thống (1 tác giả đang viết mã + 1 reviewer đang kiểm tra).
- Quy định này ngăn chặn tình trạng cạn kiệt hạn mức gọi API (rate limits), giảm tải tài nguyên máy trạm và bảo đảm Thái luôn kiểm soát được 100% các luồng thay đổi đang diễn ra.

*Vì sao làm vậy:* Việc cô lập qua worktree và khóa file nóng giải quyết tận gốc nguyên nhân gây xung đột Git (merge conflicts) – vấn đề nhức nhối nhất khi triển khai nhiều lập trình viên hoặc nhiều AI agent trên cùng một kho mã nguồn.

---

## 6. Đàm phán và giải quyết xung đột (Negotiation & Conflict Resolution)

### 6.1. Mười luật chống xung đột (10 Anti-Conflict Rules)
Quy tắc cốt lõi được ghi trong [`AGENTS.md`](file:///home/thaidevvv/orca/workspaces/Meloo/MEL-115/AGENTS.md) và bắt buộc mọi tác tử phải tuân thủ:
1. *Một ticket = Một worktree = Một branch = Một PR.*
2. *Chỉ bắt đầu khi toàn bộ ticket phụ thuộc đã squash merge vào `main`.*
3. *Chỉ sửa file trong "Phạm vi file" của ticket.*
4. *Khóa module: Không chạy song song hai ticket trùng thư mục.*
5. *Khóa file nóng: Mỗi nhãn `hot-*` chỉ một ticket nắm giữ.*
6. *Hợp đồng trước (Contract-first): API đổi qua ticket contract trước khi viết code.*
7. *Khung dựng sẵn: Dùng cấu trúc skeleton đã thiết lập sẵn từ Sprint 0.*
8. *Tối đa 2 agent cùng lúc.*
9. *Rebase lên `main` và kiểm tra lại toàn bộ test trước khi push.*
10. *Agent không merge, không approve, không đổi Jira. Chỉ Thái bấm merge.*

### 6.2. Quy trình xử lý bất đồng khi Review chéo
Khi reviewer đưa ra nhận xét phản biện trên PR:
1. **Phân loại mức độ nghiêm trọng:**
   - `Blocker` (Chặn đứng): Vi phạm bảo mật (lộ đáp án, SQL injection), vi phạm phạm vi file, vỡ build CI, sai lệch quy tắc nghiệp vụ BR cốt lõi.
   - `Major` (Nghiêm trọng): Thiếu test AC, nguy cơ race condition, N+1 query CSDL, thiếu chuỗi i18n.
   - `Minor` (Nhỏ): Định dạng mã nguồn, tối ưu hóa câu chữ, đặt tên biến.
2. **Quy trình hòa giải phản biện (Verifier Protocol):**
   - Tác giả bắt buộc phải sửa toàn bộ nhận xét `Blocker` và `Major`.
   - Nếu tác giả không đồng ý với nhận xét của reviewer: tác giả **không được tự ý bỏ qua** mà phải viết kiểm thử (test case) hoặc trích dẫn quy tắc BR/ADR để chứng minh.
   - Trong các ticket review sâu, subagent `meloo-verifier` được kích hoạt độc lập để kiểm chứng lại lập luận giữa hai bên.
   - Nếu bất đồng vẫn không thể giải quyết sau 2 vòng phản biện: **Thái là trọng tài tối cao đưa ra phán quyết kỹ thuật cuối cùng.**

### 6.3. Giải quyết xung đột mã nguồn (Git Conflict Resolution)
- Mọi nhánh tính năng đều phải thực hiện `git fetch origin && git rebase origin/main` trước khi mở PR.
- Nếu xảy ra xung đột ở các file nằm ngoài phạm vi cho phép của ticket: agent lập tức dừng lại và báo cáo để Thái xử lý, tuyệt đối không tự ý giải quyết xung đột (conflict resolution) trên file của module khác.
- Sau khi rebase thành công, toàn bộ test suite phải được chạy lại cục bộ trước khi thực hiện `git push --force-with-lease`.

*Vì sao làm vậy:* Quy trình phân loại lỗi rõ ràng và cơ chế verifier độc lập biến việc review chéo thành một cuộc tranh luận kỹ thuật dựa trên dữ liệu và bài test, thay vì những tranh cãi chủ quan; đồng thời bảo đảm nhánh `main` luôn ở trạng thái sẵn sàng triển khai (deployable).

---

## 7. Giải thích lý do thiết kế quy trình (Vì sao làm vậy)

1. **Vì sao bắt buộc nguyên tắc "Người viết khác người review, luôn luôn"?**  
   *Trả lời:* AI agent có xu hướng tự biện minh cho đoạn mã do chính nó sinh ra. Khi giao nhiệm vụ review cho một agent khác (ví dụ Claude viết code, Codex review) kèm prompt đóng vai trò phản biện khắt khe, tỷ lệ phát hiện lỗi logic, rò rỉ bộ nhớ và vi phạm quy ước tăng lên rõ rệt.
2. **Vì sao cấm triệt để agent merge PR và đổi trạng thái Jira?**  
   *Trả lời:* Đây là chốt chặn an toàn cuối cùng (Human-in-the-loop). Việc giữ lại quyền merge và cập nhật Jira cho Thái bảo đảm con người nắm giữ quyền kiểm soát tối cao đối với toàn bộ mã nguồn được đưa vào sản phẩm và chịu trách nhiệm với điểm số đồ án.
3. **Vì sao áp dụng cơ chế khóa file nóng (`hot-*`)?**  
   *Trả lời:* Các file như `openapi.yaml`, `pom.xml` hay database migration là "nút cổ chai" của toàn dự án. Nếu hai agent cùng sửa file migration một lúc, số phiên bản sẽ bị trùng lặp hoặc schema bị lệch, dẫn đến vỡ môi trường CI và tốn hàng giờ đồng hồ để sửa chữa thủ công.

---

## 8. Ngân hàng câu hỏi bảo vệ (Defense Preparation)

1. **Thầy/Cô hỏi:** *Quy trình làm việc nhóm này thể hiện tính chuyên nghiệp và đáp ứng chuẩn đầu ra CLO3 như thế nào khi nhóm chỉ có một sinh viên?*  
   **Trả lời:** Dạ thưa Thầy/Cô, chuyên nghiệp không nằm ở số lượng con người mà nằm ở phương pháp luận và kỷ luật kỹ thuật. Em đã thiết lập một quy trình công nghiệp hoàn chỉnh: có hệ thống quản lý Jira chuẩn mực, 5 tiêu chí DoR chặn đầu vào, 6 tiêu chí DoD chặn đầu ra, ma trận RACI phân vai rõ ràng, kiểm thử tự động Testcontainers, CI bảo vệ phạm vi và quy trình review phản biện độc lập. Em đóng vai trò vừa là Tech Lead kiểm soát kiến trúc, vừa là Product Owner kiểm tra tính đúng đắn của nghiệp vụ.
2. **Thầy/Cô hỏi:** *Làm sao bạn kiểm soát được việc AI agent không tự ý sửa các đoạn code quan trọng của hệ thống?*  
   **Trả lời:** Em sử dụng cơ chế bảo vệ 3 lớp: Lớp 1 là công cụ `check-scope.sh` và `scope-guard` trên CI kiểm tra danh sách file diff dựa trên `scope.txt` của ticket; lớp 2 là kiến trúc Spring Modulith chặn đứng mọi vi phạm gọi xuyên module trái phép; lớp 3 là lệnh cấm ghi trên git config và quyền merge duy nhất thuộc về em.
3. **Thầy/Cô hỏi:** *Trong quá trình làm việc, có trường hợp nào hai agent xảy ra bất đồng ý kiến về giải pháp kỹ thuật không và bạn giải quyết thế nào?*  
   **Trả lời:** Dạ có, điển hình là khi triển khai cơ chế phát nhạc bảo mật: Một bên đề xuất mã hóa stream audio bằng Web Crypto API, bên kia phản biện rằng giải pháp này gây quá tải CPU của trình duyệt di động và đề xuất dùng token mờ HMAC hết hạn 60s trên URL tĩnh (ADR-003/008). Em đã cho chạy thử nghiệm độc lập (Spike SPK-01) và quyết định chọn giải pháp token HMAC vì vừa bảo mật đáp án tuyệt đối vừa tối ưu hiệu năng phát nhạc.
