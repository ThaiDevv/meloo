package com.meloo.chat.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import com.meloo.chat.api.ReportStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChatPersistenceIntegrationTest extends IntegrationTest {

  @Autowired private ChatReportRepository chatReportRepository;

  @Test
  @DisplayName("ChatReport round-trip with jsonb context and default OPEN status")
  void chatReportRoundTrip() {
    UUID reportId = UUID.randomUUID();
    String contextJson = "[{\"sender\": \"bad_user\", \"text\": \"bad message\"}]";
    ChatReport report =
        ChatReport.builder()
            .id(reportId)
            .messageText("bad message")
            .senderId(UUID.randomUUID())
            .senderNickname("bad_user")
            .reporterId(UUID.randomUUID())
            .context(contextJson)
            .build();

    ChatReport saved = chatReportRepository.saveAndFlush(report);
    assertThat(saved.getStatus()).isEqualTo(ReportStatus.OPEN);
    assertThat(saved.getCreatedAt()).isNotNull();

    ChatReport loaded = chatReportRepository.findById(reportId).orElseThrow();
    assertThat(loaded.getMessageText()).isEqualTo("bad message");
    assertThat(loaded.getContext()).contains("bad_user");
  }
}
