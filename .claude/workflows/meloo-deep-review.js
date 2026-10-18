export const meta = {
  name: 'meloo-deep-review',
  description: 'Review sâu một PR hoặc một diff của Meloo theo nhiều góc nhìn, kiểm chứng phản biện từng lỗi Blocker/Major rồi tổng hợp một bản review',
  phases: [
    { title: 'Chuẩn bị', detail: 'Đọc ticket, diff và chọn góc nhìn' },
    { title: 'Review', detail: 'Mỗi góc nhìn một reviewer độc lập' },
    { title: 'Kiểm chứng', detail: 'Mỗi lỗi Blocker/Major một verifier tìm cách bác bỏ' },
    { title: 'Tổng hợp', detail: 'Khử trùng lặp, xếp mức độ, ghi file review' },
  ],
}

// args:
//   pr        số PR (review chéo; chạy sau khi đã gh pr checkout)
//   range     khoảng diff khi tự review trước khi mở PR, VD "origin/main...HEAD" (kèm ticket)
//   ticket    đường dẫn file ticket trong _workspace/
//   ws        thư mục ghi kết quả (mặc định _workspace/review/PR-<số> hoặc _workspace/self-review)
//   verifiers 1 (mặc định) hoặc 3 (lấy đa số, tốn hạn mức hơn)
//   economy   true thì reviewer chạy sonnet thay opus (khi sắp hết hạn mức)
const a = args ?? {}
if (!a.pr && !a.range) {
  return { error: 'Cần args.pr (số PR), hoặc args.range kèm args.ticket.' }
}
const target = a.pr ? `PR #${a.pr}` : `diff ${a.range}`
const ws = a.ws ?? (a.pr ? `_workspace/review/PR-${a.pr}` : '_workspace/self-review')
const verifierCount = a.verifiers === 3 ? 3 : 1
const reviewerModel = a.economy ? 'sonnet' : 'opus'

const LENSES = {
  security: 'Bảo mật và lộ đáp án: phân quyền mọi endpoint và destination STOMP, validate input, token (JWT, refresh, audio HMAC), không lộ đáp án trước ROUND_END qua API, sự kiện hay log, không có secret.',
  data: 'Dữ liệu: transaction đúng tầng, không gọi HTTP ngoài trong transaction, N+1, phân trang, index, migration, listener sự kiện idempotent, ranh giới module Modulith.',
  concurrency: 'Đồng thời và chất lượng test: mọi thay đổi trạng thái phòng đi qua hàng đợi của phòng; race khi hết giờ, khi đoán cùng lúc, khi tranh chỗ cuối; mất kết nối; test có kiểm hành vi thật và đủ mục Test bắt buộc.',
  feData: 'Frontend dùng hợp đồng và state: client sinh từ OpenAPI, không any hay as, bóc đúng lớp bọc, query key và invalidate, store realtime, đồng hồ theo serverTime, dọn subscribe và timer.',
  feUx: 'Frontend a11y, i18n và trạng thái UI: bàn phím, aria, tương phản, chuỗi cứng, đủ key vi/en, loading/lỗi/rỗng, responsive 360px, Web Audio phát đúng mức.',
  feSec: 'Frontend test và bảo mật phía client: token chỉ trong bộ nhớ, refresh một lần, không dangerouslySetInnerHTML, không đưa đáp án vào DOM sớm; test component theo AC.',
}

const CONTEXT = { type: 'object', required: ['contextPath', 'hasBackend', 'hasFrontend', 'changedFiles'], properties: {
  contextPath: { type: 'string', minLength: 1 },
  hasBackend: { type: 'boolean' },
  hasFrontend: { type: 'boolean' },
  changedFiles: { type: 'array', items: { type: 'string' } },
  author: { type: 'string' },
} }

const FINDINGS = { type: 'object', required: ['findings'], properties: {
  findings: { type: 'array', items: { type: 'object',
    required: ['severity', 'file', 'title', 'why', 'evidence'], properties: {
      severity: { type: 'string', enum: ['Blocker', 'Major', 'Minor'] },
      file: { type: 'string' },
      title: { type: 'string' },
      why: { type: 'string' },
      evidence: { type: 'string' },
      fix: { type: 'string' },
    } } },
} }

const VERDICT = { type: 'object', required: ['status', 'reason'], properties: {
  status: { type: 'string', enum: ['confirmed', 'refuted', 'uncertain'] },
  severity: { type: 'string', enum: ['Blocker', 'Major', 'Minor'] },
  reason: { type: 'string' },
  evidence: { type: 'string' },
} }

const REPORT = { type: 'object', required: ['path', 'blockers', 'majors', 'minors'], properties: {
  path: { type: 'string', minLength: 1 },
  blockers: { type: 'integer' },
  majors: { type: 'integer' },
  minors: { type: 'integer' },
} }

phase('Chuẩn bị')
const ctx = await agent(
  `Chuẩn bị review sâu cho ${target} của dự án Meloo. ` +
  (a.pr
    ? `Chạy gh pr view ${a.pr} --json title,body,files,headRefName và gh pr diff ${a.pr}. `
    : `Chạy git diff --stat ${a.range} và git diff ${a.range}; ticket nằm ở ${a.ticket}. `) +
  `Ghi vào ${ws}/00_context.md: Acceptance Criteria, Phạm vi file (khối meloo:scope), mức review, tác giả, danh sách file đổi và tóm tắt diff theo từng file. ` +
  'Trả về đường dẫn file đó, diff có chạm backend/ hay frontend/ không, danh sách file đổi và tác giả.',
  { label: 'chuẩn bị', phase: 'Chuẩn bị', schema: CONTEXT, model: 'sonnet' })
if (!ctx) return { error: 'Không chuẩn bị được ngữ cảnh review.' }

const keys = []
if (ctx.hasBackend) keys.push('security', 'data', 'concurrency')
if (ctx.hasFrontend) keys.push('feData', 'feUx', 'feSec')
if (!keys.length) keys.push('security', 'data', 'concurrency')
const lensKeys = keys.length > 4 ? ['security', 'concurrency', 'feData', 'feSec'] : keys
log(`Góc nhìn: ${lensKeys.join(', ')} (${lensKeys.length} reviewer, model ${reviewerModel}, ${verifierCount} verifier mỗi lỗi).`)

const reviewed = await pipeline(
  lensKeys,
  key => agent(
    `Review ${target} của Meloo theo MỘT góc nhìn: ${LENSES[key]} ` +
    `Ngữ cảnh ở ${ctx.contextPath}. Đọc toàn bộ diff và code liên quan, dùng checklist của skill meloo-review. ` +
    'Chỉ báo lỗi có bằng chứng file:dòng. Không sửa file. Trả về danh sách nhận xét.',
    { label: `review:${key}`, phase: 'Review', agentType: 'meloo-reviewer', model: reviewerModel, schema: FINDINGS }),
  (review, key) => {
    if (!review) return null
    const serious = review.findings.filter(f => f.severity !== 'Minor')
    const minors = review.findings.filter(f => f.severity === 'Minor').map(f => ({ ...f, lens: key, verdict: null }))
    return parallel(serious.map(f => () =>
      parallel(Array.from({ length: verifierCount }, (_, i) => () =>
        agent(
          `Kiểm chứng phản biện nhận xét sau về ${target} (Meloo). Tìm cách bác bỏ trước; chỉ trả confirmed khi chỉ ra được đường đi dẫn tới lỗi. ` +
          `Ngữ cảnh ở ${ctx.contextPath}. Nhận xét: ${JSON.stringify(f)}` +
          (i > 0 ? ` (người kiểm chứng thứ ${i + 1}, xét độc lập)` : ''),
          { label: `kiểm chứng:${f.file}`, phase: 'Kiểm chứng', agentType: 'meloo-verifier', schema: VERDICT })))
        .then(votes => {
          const ok = votes.filter(Boolean)
          const confirmed = ok.filter(v => v.status === 'confirmed')
          let status = 'refuted'
          if (confirmed.length * 2 > verifierCount) status = 'confirmed'
          else if (ok.length < verifierCount || ok.some(v => v.status === 'uncertain')) status = 'uncertain'
          const severity = (confirmed[0] && confirmed[0].severity) || f.severity
          return { ...f, lens: key, severity, verdict: { status, reasons: ok.map(v => v.reason) } }
        })))
      .then(verified => [...verified.filter(Boolean), ...minors])
  },
)

const failedLenses = reviewed.filter(r => r === null).length
const all = reviewed.filter(Boolean).flat().filter(Boolean)
const kept = all.filter(f => f.verdict === null || f.verdict.status === 'confirmed')
const questions = all.filter(f => f.verdict && f.verdict.status === 'uncertain')
const dropped = all.filter(f => f.verdict && f.verdict.status === 'refuted')
log(`Nhận xét: ${all.length} · giữ ${kept.length} · thành câu hỏi ${questions.length} · bỏ ${dropped.length}` +
  (failedLenses ? ` · ${failedLenses} góc nhìn không chạy được` : ''))

phase('Tổng hợp')
const report = await agent(
  `Tổng hợp review sâu ${target} của Meloo thành MỘT bản theo mục "Định dạng nhận xét" của skill meloo-review. ` +
  `Khử trùng lặp: cùng file và cùng vấn đề thì gộp, giữ mức cao nhất. Nhận xét giữ lại: ${JSON.stringify(kept)}. ` +
  `Đưa vào "Câu hỏi cho tác giả": ${JSON.stringify(questions)}. ` +
  `Trong "Đã kiểm tra" ghi: đã bỏ ${dropped.length} nhận xét vì kiểm chứng bác bỏ, mỗi nhận xét một dòng (vấn đề, lý do bỏ): ` +
  JSON.stringify(dropped.map(f => ({ file: f.file, title: f.title, reasons: f.verdict.reasons }))) +
  (failedLenses ? `; ${failedLenses} góc nhìn bị lỗi nên phần đó CHƯA được review` : '') + '. ' +
  `Ghi file ${ws}/review-deep.md rồi trả về đường dẫn cùng số Blocker, Major, Minor. Không đăng lên PR.`,
  { label: 'tổng hợp', phase: 'Tổng hợp', schema: REPORT, model: 'sonnet' })
if (!report) return { error: 'Không tổng hợp được bản review.', kept, questions, droppedCount: dropped.length }

return { report, droppedCount: dropped.length, questionCount: questions.length, failedLenses }
