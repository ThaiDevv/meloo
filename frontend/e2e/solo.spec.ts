import { expect, test } from '@playwright/test'

test.describe('Solo Game Flow (MEL-50 / QA-01)', () => {
  test('guest plays solo match with combo and scores verified on summary', async ({ page }) => {
    // -------------------------------------------------------------
    // 1. Khách: Tạo phiên khách từ /start
    // -------------------------------------------------------------
    await page.goto('/start')

    const nicknameInput = page.locator('#guest-nickname')
    await expect(nicknameInput).toBeVisible()
    await nicknameInput.fill('MelooTester')

    const submitGuestBtn = page.getByRole('button', { name: /Vào chơi|Start playing|Tiếp tục/i })
    await submitGuestBtn.click()

    // Sau khi trở thành khách, chuyển hướng tới /solo
    await page.goto('/solo')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()

    // -------------------------------------------------------------
    // 2. Chọn bộ: Chọn chế độ và số vòng (5 vòng)
    // -------------------------------------------------------------
    // Click vào tile chọn 5 vòng
    const fiveRoundsTile = page.locator('label').filter({ hasText: /^5\s*(rounds|vòng)?$/i })
    if (await fiveRoundsTile.isVisible()) {
      await fiveRoundsTile.click()
    }

    const startMatchBtn = page.getByRole('button', { name: /Vào trận|Start match|Bắt đầu/i })
    await expect(startMatchBtn).toBeVisible()
    await startMatchBtn.click()

    // -------------------------------------------------------------
    // Màn Intro: Vào vòng 1
    // -------------------------------------------------------------
    const enterRound1Btn = page.getByRole('button', {
      name: /Vào vòng 1|Start round 1|Chơi tiếp vòng 1|Bắt đầu vòng/i,
    })
    await expect(enterRound1Btn).toBeVisible({ timeout: 10_000 })
    await enterRound1Btn.click()

    // -------------------------------------------------------------
    // 3. Vòng 1: Đúng ở 2s (mức 1)
    // Điểm cơ bản: 1.000 điểm, combo streak = 1 (combo 0%)
    // -------------------------------------------------------------
    const guessInput = page.getByRole('combobox', { name: /Đoán tên bài|Guess the song|Guess/i })
    await expect(guessInput).toBeVisible({ timeout: 10_000 })
    await guessInput.fill('Chúng Ta Của Hiện Tại')

    const suggestion1 = page.getByRole('option', { name: /Chúng Ta Của Hiện Tại/i })
    await expect(suggestion1).toBeVisible({ timeout: 5_000 })
    await suggestion1.click()

    const submitGuessBtn = page.getByRole('button', { name: /Đoán|Guess/i, exact: true })
    await submitGuessBtn.click()

    // Xác nhận kết quả vòng 1: đúng ở 2s (+1.000 điểm)
    const nextRound1Btn = page.getByRole('button', {
      name: /Vòng tiếp theo|Next round|Tiếp tục/i,
    })
    await expect(nextRound1Btn).toBeVisible({ timeout: 10_000 })
    await nextRound1Btn.click()

    // -------------------------------------------------------------
    // 4. Vòng 2: Nghe thêm rồi đoán đúng ở mức 5s (mức 2)
    // Điểm cơ bản: 800 + thưởng combo 10% (80) = 880 điểm.
    // Tổng điểm: 1.000 + 880 = 1.880 điểm, combo streak = 2
    // -------------------------------------------------------------
    const listenMoreBtn = page.getByRole('button', { name: /Nghe thêm|Reveal more|Hear more/i })
    await expect(listenMoreBtn).toBeVisible({ timeout: 10_000 })
    await listenMoreBtn.click()

    // Đoán bài vòng 2
    await expect(guessInput).toBeVisible({ timeout: 10_000 })
    await guessInput.fill('Waiting For You')

    const suggestion2 = page.getByRole('option', { name: /Waiting For You/i })
    await expect(suggestion2).toBeVisible({ timeout: 5_000 })
    await suggestion2.click()

    await submitGuessBtn.click()

    // Xác nhận kết quả vòng 2: đúng ở mức 5s (+880 điểm, combo 10%)
    const nextRound2Btn = page.getByRole('button', {
      name: /Vòng tiếp theo|Next round|Tiếp tục/i,
    })
    await expect(nextRound2Btn).toBeVisible({ timeout: 10_000 })
    await nextRound2Btn.click()

    // -------------------------------------------------------------
    // 5. Vòng 3: Sai hết lượt (0 điểm, đứt chuỗi combo)
    // Đoán sai lần lượt qua các mức 2s -> 5s -> 10s -> 20s -> 30s
    // -------------------------------------------------------------
    for (let level = 1; level <= 5; level++) {
      await expect(guessInput).toBeVisible({ timeout: 10_000 })
      await guessInput.fill('Chung Ta Cua Tuong Lai')

      const decoyOption = page.getByRole('option', { name: /Chúng Ta Của Tương Lai/i })
      await expect(decoyOption).toBeVisible({ timeout: 5_000 })
      await decoyOption.click()

      await submitGuessBtn.click()
    }

    // Kết thúc vòng 3 với 0 điểm (Hết lượt)
    const nextRound3Btn = page.getByRole('button', {
      name: /Vòng tiếp theo|Next round|Tiếp tục/i,
    })
    await expect(nextRound3Btn).toBeVisible({ timeout: 10_000 })
    await nextRound3Btn.click()

    // -------------------------------------------------------------
    // Vòng 4: Bỏ qua ở mức 30s (0 điểm)
    // -------------------------------------------------------------
    for (let i = 0; i < 4; i++) {
      const moreBtn = page.getByRole('button', { name: /Nghe thêm|Reveal more|Hear more/i })
      await expect(moreBtn).toBeVisible({ timeout: 10_000 })
      await moreBtn.click()
    }
    const skipBtnRound4 = page.getByRole('button', { name: /Bỏ qua|Give up|Skip/i })
    await expect(skipBtnRound4).toBeVisible({ timeout: 10_000 })
    await skipBtnRound4.click()

    const nextRound4Btn = page.getByRole('button', {
      name: /Vòng tiếp theo|Next round|Tiếp tục/i,
    })
    await expect(nextRound4Btn).toBeVisible({ timeout: 10_000 })
    await nextRound4Btn.click()

    // -------------------------------------------------------------
    // Vòng 5: Bỏ qua ở mức 30s (0 điểm) -> Kết thúc trận
    // -------------------------------------------------------------
    for (let i = 0; i < 4; i++) {
      const moreBtn = page.getByRole('button', { name: /Nghe thêm|Reveal more|Hear more/i })
      await expect(moreBtn).toBeVisible({ timeout: 10_000 })
      await moreBtn.click()
    }
    const skipBtnRound5 = page.getByRole('button', { name: /Bỏ qua|Give up|Skip/i })
    await expect(skipBtnRound5).toBeVisible({ timeout: 10_000 })
    await skipBtnRound5.click()

    // Nút xem tổng kết sau khi hết vòng 5
    const finishBtn = page.getByRole('button', { name: /Xem kết quả|See result|Xem tổng kết/i })
    await expect(finishBtn).toBeVisible({ timeout: 10_000 })
    await finishBtn.click()

    // -------------------------------------------------------------
    // 6. Tổng kết: Kiểm tra tổng điểm, combo và số câu đúng
    // -------------------------------------------------------------
    // Tiêu đề tổng kết
    const summaryHeading = page.getByRole('heading', {
      name: /Tổng kết|Summary/i,
    })
    await expect(summaryHeading).toBeVisible({ timeout: 10_000 })

    // Tổng điểm: 1.000 (vòng 1) + 880 (vòng 2) + 0 (vòng 3) + 0 + 0 = 1.880 điểm (BR-08)
    const totalScoreElement = page.getByTestId('match-total')
    await expect(totalScoreElement).toBeVisible()
    await expect(totalScoreElement).toHaveText(/1[.,]880/)

    // Số bài đoán đúng: 2 / 5
    await expect(page.getByText(/2\s*[/]\s*5/)).toBeVisible()

    // Combo dài nhất: 2 vòng
    await expect(page.getByText(/combo.*2/i)).toBeVisible()

    // Các nút thao tác ở màn tổng kết
    await expect(page.getByRole('button', { name: /Chơi lại|Play again/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Chia sẻ|Share/i })).toBeVisible()
  })
})
