---
name: meloo-review
description: "Review chéo PR của dự án Meloo theo ticket MEL: đối chiếu Acceptance Criteria và Phạm vi file, soi bảo mật, lộ đáp án, N+1, transaction, race condition, chất lượng test, i18n và a11y; kiểm chứng phản biện từng lỗi Blocker/Major trước khi đăng nhận xét lên PR (chỉ comment, không approve, không sửa code). Dùng khi người dùng nói 'review PR 12', '/meloo-review', '$meloo-review', 'review giúp PR của Codex/Claude', 'review lại sau khi sửa', 'review sâu', hoặc 'rà bảo mật SEC-01' (chế độ audit). Không dùng khi người dùng muốn bạn tự làm hoặc sửa ticket (dùng meloo-ticket)."
---

# meloo-review — review chéo có kiểm chứng

Skill này thay prompt "Review" ở Phụ lục B. Mục tiêu: Thái (không đọc được React) nhận về danh sách lỗi THẬT, có bằng chứng, xếp theo mức độ, chứ không phải một danh sách nghi ngờ.

## Chế độ chạy
| Mức | Khi nào | Cách chạy |
|---|---|---|
| Chuẩn (mặc định) | Ticket ghi "review chuẩn" | Phiên chính review theo checklist → mỗi Blocker/Major gọi một `meloo-verifier` (chạy song song) → đăng nhận xét |
| Sâu | Ticket ghi "review sâu", hoặc diff chạm identity/audio/room/chat/voice/admin, `hot-shared`, `hot-ci` | Workflow `/meloo-deep-review` nếu có (Claude Code, đã bật Dynamic workflows); không có thì 3 subagent `meloo-reviewer` theo 3 góc nhìn, rồi verifier |
| Audit | SEC-01, hoặc Thái yêu cầu rà cả repo | Như mức sâu nhưng phạm vi là cả repo; kết quả ghi vào `docs/security/review.md` |

Phiên review cũng tốn hạn mức như một agent (luật 8: tối đa 2 agent cùng lúc).

## Bước 1 — Thu thập
1. `gh pr view <số> --json number,title,body,headRefName,baseRefName,author,additions,deletions,files` và `gh pr diff <số>`.
2. Ticket: lấy AC và Phạm vi file trong mô tả PR (khối `meloo:scope`, bảng AC). Có Jira MCP thì đối chiếu thêm (chỉ đọc).
3. Người viết phải khác người review: PR ghi "Tác giả: Codex" thì chỉ Claude review, và ngược lại. Nếu bạn là tác giả thì dừng, báo Thái.
4. Lấy code về để đọc và chạy test: trong worktree review của Orca, chạy `gh pr checkout <số>`.
5. Lưu ngữ cảnh vào `_workspace/review/PR-<số>/00_context.md`.

## Bước 2 — Kiểm tra cứng (không cần phán đoán)
- Phạm vi: `bash .claude/skills/meloo-ticket/scripts/check-scope.sh --pr <số>`. File ngoài phạm vi là Blocker (luật 3), trừ khi PR đã giải thích và Thái đồng ý.
- CI: `gh pr checks <số>`. Đỏ thì ghi rõ job nào; không review chi tiết phần đang đỏ vì nó sẽ còn đổi.
- Kích thước: hơn ~400 dòng (không tính code sinh) thì ghi một Minor, đề xuất tách ở lần sau.

## Bước 3 — Review theo checklist
Đọc toàn bộ diff, sau đó đọc thêm nơi gọi và nơi được gọi. Checklist theo lane:
- Backend: `references/checklist-backend.md`
- Frontend: `references/checklist-frontend.md`
- Hợp đồng, migration, CI/hạ tầng, tài liệu: `references/checklist-other.md`

Luôn soi đủ 7 điểm gốc:
1. Đủ AC chưa; thiếu edge case nào (dữ liệu rỗng, lỗi mạng, hết giờ).
2. Có file ngoài phạm vi không.
3. Bảo mật: phân quyền mọi endpoint và destination, validate input, không lộ đáp án, không secret.
4. Dữ liệu: N+1, thiếu index, `@Transactional` sai tầng, gọi HTTP ngoài trong transaction.
5. Đồng thời: trạng thái phòng chỉ đổi trong hàng đợi của phòng; race khi hết giờ hoặc hai người đoán cùng lúc.
6. Test kiểm tra hành vi thật, hay chỉ để đủ coverage.
7. Frontend: đủ i18n vi/en, có trạng thái loading/lỗi/rỗng, dùng được bằng bàn phím.

Mỗi nhận xét cần có: mức (Blocker/Major/Minor), file:dòng, vấn đề, vì sao sai (dẫn AC, BR hoặc luật), cách tái hiện hoặc test chứng minh, đề xuất sửa.

## Bước 4 — Kiểm chứng phản biện
Mỗi nhận xét Blocker hoặc Major giao cho một subagent `meloo-verifier`; gửi tất cả trong cùng một lượt để chúng chạy song song. Truyền nhận xét (dạng JSON), đường dẫn worktree và ticket.
- `confirmed`: giữ lại. Verifier đề xuất hạ mức thì theo mức đó.
- `uncertain`: chuyển xuống "Câu hỏi cho tác giả".
- `refuted`: bỏ khỏi danh sách lỗi, nhưng ghi một dòng (vấn đề, lý do bỏ) ở cuối mục "Đã kiểm tra". Verifier có thể bác nhầm; dòng này giúp tác giả và Thái vẫn thấy nhận xét đó.

Không gọi được subagent (Codex chưa có agent, hoặc chính bạn đang chạy dưới dạng subagent) thì tự phản biện từng nhận xét theo đúng tiêu chí của `meloo-verifier`: chỉ bác bỏ khi chỉ ra được dòng code hoặc cấu hình chặn lỗi; kết luận phụ thuộc vào code không có trong repo thì chuyển thành câu hỏi. Ghi rõ "tự kiểm chứng".

Vì sao phải kiểm chứng: báo lỗi giả làm tác giả sửa nhầm và làm Thái mất niềm tin vào review. Còn lỗi thật lọt qua thì vẫn có QA và E2E làm lưới thứ hai.

## Bước 5 — Đăng nhận xét
1. Viết `_workspace/review/PR-<số>/review-round<k>.md` theo mẫu bên dưới.
2. `gh pr comment <số> --body-file _workspace/review/PR-<số>/review-round<k>.md`. Mọi PR đều do tài khoản GitHub của Thái tạo, nên GitHub không cho approve hay request changes; chỉ dùng comment. Không bao giờ approve.
3. Báo Thái: kết luận, số Blocker/Major, việc tiếp theo (tác giả chạy "sửa theo review", hoặc Thái nghiệm thu).

## Định dạng nhận xét
```markdown
## Review MEL-<số> · PR #<số> · vòng <k> · reviewer: <Claude|Codex>
**Kết luận:** Chặn merge — còn <n> Blocker, <m> Major | Không còn Blocker/Major — chờ Thái nghiệm thu

### Blocker
1. **[B1]** `đường/dẫn/File.java:88` — <vấn đề, một câu>.
   Vì sao: <AC/BR/luật bị vi phạm>. Tái hiện: <bước hoặc test>. Đề xuất: <cách sửa>.
### Major
### Minor
### Câu hỏi cho tác giả
### Đã kiểm tra
- Phạm vi file: <OK | danh sách file ngoài phạm vi> · CI: <xanh | job đỏ>
- AC: <x>/<y> có bằng chứng · Kiểm chứng: <a> giữ, <b> chuyển thành câu hỏi, <c> bỏ
- Đã bỏ sau kiểm chứng: <vấn đề> — <dòng code chặn lỗi> (mỗi nhận xét bị bỏ một dòng)
```

## Vòng sau
Đọc review vòng trước và phần trả lời của tác giả. Với từng mục, ghi: đã sửa, chưa sửa, hay tác giả phản bác (đánh giá bằng chứng của họ). Chỉ thêm nhận xét mới khi diff mới sinh ra lỗi. Sau 2 vòng mà vẫn tranh cãi thì ghi rõ lập luận hai phía, để Thái quyết.

## Review sâu
- Có workflow: chạy `/meloo-deep-review` với args `{ "pr": <số> }` sau khi đã `gh pr checkout`. Khi tự review trước khi mở PR thì dùng `{ "range": "origin/main...HEAD", "ticket": "<file ticket>" }`. Workflow tự chia góc nhìn, kiểm chứng và ghi file review. Bạn đọc lại rồi đăng như bước 5.
- Không có workflow: chọn 3 góc nhìn hợp với diff theo bảng dưới, gọi 3 `meloo-reviewer` song song trong một lượt (mỗi cái một góc nhìn), gộp và khử trùng lặp, rồi làm bước 4.

| Diff chạm | Góc nhìn |
|---|---|
| Backend | bảo mật + lộ đáp án · dữ liệu (transaction, N+1, migration) · đồng thời + chất lượng test |
| Frontend | dùng hợp đồng + state + realtime · a11y + i18n + trạng thái UI · test + bảo mật phía client (XSS, token) |
| Cả hai | 3–4 góc nhìn rủi ro nhất trong 6 góc trên |

## Audit (SEC-01)
1. Lập danh mục: mọi endpoint REST (từ `contracts/openapi.yaml` và controller), mọi destination STOMP (`python3 .claude/skills/meloo-qa-check/scripts/stomp-destinations.py`), mọi chỗ cấp token.
2. Mỗi mục OWASP Top 10 là một góc nhìn; chạy như review sâu, phạm vi cả repo.
3. Ghi kết quả vào `docs/security/review.md` dưới dạng bảng: mục kiểm tra → kết quả → bằng chứng. Mỗi lỗi confirmed thì soạn sẵn nội dung ticket Bug (mức Critical/High/Medium/Low, cách tái hiện) để Thái tạo trên Jira. Ticket audit không sửa code.

## Lỗi
- Không có quyền `gh` hoặc không checkout được: review trên diff và ghi "chưa chạy test".
- Verifier lỗi hoặc hết hạn mức: ghi nhận xét đó là "chưa kiểm chứng", không tự nâng thành confirmed.
- Hết hạn mức giữa chừng: lưu bản nháp vào `_workspace/review/`, báo Thái, không thử lại liên tục.

## Kịch bản kiểm thử
**Bình thường:** "/meloo-review 12" (PR của Codex cho MEL-45) → check-scope đạt, CI xanh → checklist backend ra 3 nhận xét (1 Blocker lộ `trackId` trong response vòng, 1 Major N+1, 1 Minor) → 2 verifier: Blocker confirmed, Major refuted vì đã có `@EntityGraph` → đăng comment "Chặn merge — còn 1 Blocker" với ghi chú "bỏ 1 nhận xét sau kiểm chứng".

**Lỗi:** "/meloo-review 13" mà PR ghi "Tác giả: Claude" và bạn là Claude → dừng: "Mình là tác giả của PR này nên không review được (luật người viết khác người review). Hãy mở phiên Codex và chạy `$meloo-review 13`."
