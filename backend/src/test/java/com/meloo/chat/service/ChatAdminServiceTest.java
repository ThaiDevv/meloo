package com.meloo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meloo.chat.api.BannedWordDto;
import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.api.ChatReportDto;
import com.meloo.chat.api.ReportStatus;
import com.meloo.chat.config.ChatProperties;
import com.meloo.chat.persistence.BannedWord;
import com.meloo.chat.persistence.BannedWordRepository;
import com.meloo.chat.persistence.ChatReport;
import com.meloo.chat.persistence.ChatReportRepository;
import com.meloo.shared.api.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ChatAdminServiceTest {

  private BannedWordRepository bannedWordRepository;
  private ChatReportRepository chatReportRepository;
  private BannedWordService bannedWordService;
  private ChatHistoryManager historyManager;
  private ChatAdminServiceImpl adminService;

  @BeforeEach
  void setUp() {
    bannedWordRepository = mock(BannedWordRepository.class);
    chatReportRepository = mock(ChatReportRepository.class);
    bannedWordService = mock(BannedWordService.class);
    ChatProperties properties =
        new ChatProperties(Duration.ofSeconds(1), 200, Duration.ofMinutes(5), 50);
    historyManager = new ChatHistoryManager(properties);
    ObjectMapper objectMapper = new ObjectMapper();

    adminService =
        new ChatAdminServiceImpl(
            bannedWordRepository,
            chatReportRepository,
            bannedWordService,
            historyManager,
            objectMapper);
  }

  @Test
  @DisplayName("Adds a banned word, normalizes to lowercase, and invalidates cache (BR-17)")
  void addBannedWord() {
    when(bannedWordRepository.save(any(BannedWord.class))).thenAnswer(inv -> inv.getArgument(0));

    BannedWordDto result = adminService.addBannedWord("  ĐỤ MÁ  ", UUID.randomUUID());

    assertThat(result.word()).isEqualTo("đụ má");
    verify(bannedWordRepository).save(any(BannedWord.class));
    verify(bannedWordService).invalidateCache();
  }

  @Test
  @DisplayName("Rejects blank banned word")
  void rejectsBlankBannedWord() {
    assertThatThrownBy(() -> adminService.addBannedWord("   ", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("blank");
  }

  @Test
  @DisplayName("Updates a banned word and invalidates cache")
  void updateBannedWord() {
    UUID id = UUID.randomUUID();
    BannedWord existing = BannedWord.builder().id(id).word("cu").createdAt(Instant.now()).build();
    when(bannedWordRepository.findById(id)).thenReturn(Optional.of(existing));
    when(bannedWordRepository.save(any(BannedWord.class))).thenAnswer(inv -> inv.getArgument(0));

    BannedWordDto updated = adminService.updateBannedWord(id, "cac");

    assertThat(updated.word()).isEqualTo("cac");
    verify(bannedWordService).invalidateCache();
  }

  @Test
  @DisplayName("Deletes a banned word and invalidates cache")
  void deleteBannedWord() {
    UUID id = UUID.randomUUID();
    when(bannedWordRepository.existsById(id)).thenReturn(true);

    adminService.deleteBannedWord(id);

    verify(bannedWordRepository).deleteById(id);
    verify(bannedWordService).invalidateCache();
  }

  @Test
  @DisplayName("Reports message and captures up to 5 preceding messages as context")
  void reportMessageWithFiveContextMessages() {
    UUID roomId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID reporterId = UUID.randomUUID();

    // Record 7 messages into the room: M1, M2, M3, M4, M5, M6, M7
    for (int i = 1; i <= 6; i++) {
      historyManager.recordMessage(
          roomId,
          new ChatContextMessage(
              UUID.randomUUID(),
              UUID.randomUUID(),
              "User" + i,
              "Message " + i,
              Instant.now().minusSeconds(10 - i)));
    }

    UUID offensiveMsgId = UUID.randomUUID();
    historyManager.recordMessage(
        roomId,
        new ChatContextMessage(
            offensiveMsgId, senderId, "BadGuy", "Nội dung phản cảm", Instant.now()));

    when(chatReportRepository.save(any(ChatReport.class))).thenAnswer(inv -> inv.getArgument(0));

    ChatReportDto report = adminService.reportMessage(roomId, offensiveMsgId, reporterId);

    assertThat(report.messageText()).isEqualTo("Nội dung phản cảm");
    assertThat(report.senderId()).isEqualTo(senderId);
    assertThat(report.senderNickname()).isEqualTo("BadGuy");
    assertThat(report.reporterId()).isEqualTo(reporterId);
    assertThat(report.status()).isEqualTo(ReportStatus.OPEN);

    // Exactly 5 preceding messages (M2, M3, M4, M5, M6)
    assertThat(report.context()).hasSize(5);
    assertThat(report.context().get(0).content()).isEqualTo("Message 2");
    assertThat(report.context().get(4).content()).isEqualTo("Message 6");
  }

  @Test
  @DisplayName("Handles report with ACTIONED or DISMISSED and records handler info")
  void handleReport() {
    UUID reportId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    ChatReport report =
        ChatReport.builder()
            .id(reportId)
            .messageText("offensive")
            .senderId(UUID.randomUUID())
            .senderNickname("bad")
            .reporterId(UUID.randomUUID())
            .status(ReportStatus.OPEN)
            .context("[]")
            .createdAt(Instant.now())
            .build();

    when(chatReportRepository.findById(reportId)).thenReturn(Optional.of(report));
    when(chatReportRepository.save(any(ChatReport.class))).thenAnswer(inv -> inv.getArgument(0));

    ChatReportDto handled = adminService.handleReport(reportId, ReportStatus.ACTIONED, adminId);

    assertThat(handled.status()).isEqualTo(ReportStatus.ACTIONED);
    assertThat(handled.handledBy()).isEqualTo(adminId);
    assertThat(handled.handledAt()).isNotNull();
  }

  @Test
  @DisplayName("Lists reports ordered by created_at desc with optional status filter")
  void listReports() {
    ChatReport r1 =
        ChatReport.builder()
            .id(UUID.randomUUID())
            .messageText("msg1")
            .senderId(UUID.randomUUID())
            .senderNickname("u1")
            .reporterId(UUID.randomUUID())
            .status(ReportStatus.OPEN)
            .context("[]")
            .createdAt(Instant.now())
            .build();

    when(chatReportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.OPEN))
        .thenReturn(List.of(r1));
    when(chatReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r1));

    List<ChatReportDto> filtered = adminService.listReports(ReportStatus.OPEN);
    assertThat(filtered).hasSize(1);
    assertThat(filtered.get(0).status()).isEqualTo(ReportStatus.OPEN);
    verify(chatReportRepository).findByStatusOrderByCreatedAtDesc(ReportStatus.OPEN);

    List<ChatReportDto> all = adminService.listReports(null);
    assertThat(all).hasSize(1);
    verify(chatReportRepository).findAllByOrderByCreatedAtDesc();
  }

  @Test
  @DisplayName("Rejects setting report status back to OPEN")
  void rejectsOpenAction() {
    UUID reportId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();

    assertThatThrownBy(() -> adminService.handleReport(reportId, ReportStatus.OPEN, adminId))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("DISMISSED or ACTIONED");
  }
}
