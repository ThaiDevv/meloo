package com.meloo.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import com.meloo.chat.api.BannedWordDto;
import com.meloo.chat.api.ChatAdminApi;
import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.api.ChatReportDto;
import com.meloo.chat.api.ReportStatus;
import com.meloo.chat.service.ChatHistoryManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChatIntegrationTest extends IntegrationTest {

  @Autowired private ChatAdminApi chatAdminApi;
  @Autowired private ChatHistoryManager historyManager;

  @Test
  @DisplayName(
      "Full integration of ChatAdminApi with PostgreSQL: banned words and report life-cycle")
  void bannedWordsAndReportLifeCycle() {
    UUID adminId = UUID.randomUUID();

    // 1. Add banned word
    BannedWordDto added = chatAdminApi.addBannedWord("từ cấm test", adminId);
    assertThat(added.word()).isEqualTo("từ cấm test");
    assertThat(added.id()).isNotNull();

    // 2. Check contains banned word
    assertThat(chatAdminApi.containsBannedWord("đây là từ cấm test nha")).isTrue();

    // 3. Update banned word
    BannedWordDto updated = chatAdminApi.updateBannedWord(added.id(), "từ cấm sửa");
    assertThat(updated.word()).isEqualTo("từ cấm sửa");
    assertThat(chatAdminApi.containsBannedWord("đây là từ cấm sửa nha")).isTrue();

    // 4. Record messages in room history and report one of them
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID reporterId = UUID.randomUUID();

    for (int i = 1; i <= 6; i++) {
      historyManager.recordMessage(
          roomId,
          new ChatContextMessage(
              UUID.randomUUID(),
              UUID.randomUUID(),
              "Player" + i,
              "Tin nhắn trước " + i,
              Instant.now()));
    }

    UUID targetMsgId = UUID.randomUUID();
    historyManager.recordMessage(
        roomId,
        new ChatContextMessage(
            targetMsgId, senderId, "Troublemaker", "Tin nhắn xúc phạm", Instant.now()));

    // 5. Submit report via ChatAdminApi
    ChatReportDto report = chatAdminApi.reportMessage(roomId, targetMsgId, reporterId);
    assertThat(report.messageText()).isEqualTo("Tin nhắn xúc phạm");
    assertThat(report.senderNickname()).isEqualTo("Troublemaker");
    assertThat(report.status()).isEqualTo(ReportStatus.OPEN);
    assertThat(report.context()).hasSize(5);

    // 6. List reports
    List<ChatReportDto> openReports = chatAdminApi.listReports(ReportStatus.OPEN);
    assertThat(openReports).extracting(ChatReportDto::id).contains(report.id());

    // 7. Handle report (actioned by admin)
    ChatReportDto handled = chatAdminApi.handleReport(report.id(), ReportStatus.ACTIONED, adminId);
    assertThat(handled.status()).isEqualTo(ReportStatus.ACTIONED);
    assertThat(handled.handledBy()).isEqualTo(adminId);

    // 8. Delete banned word
    chatAdminApi.deleteBannedWord(added.id());
    assertThat(chatAdminApi.findBannedWordById(added.id())).isEmpty();
  }
}
