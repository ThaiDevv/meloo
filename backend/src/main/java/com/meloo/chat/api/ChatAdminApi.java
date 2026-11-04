package com.meloo.chat.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Public Java API for the chat module, consumed by the admin module (BE-ADM-02). The admin module
 * must never access chat persistence entities directly.
 */
public interface ChatAdminApi {

  BannedWordDto addBannedWord(String word, @Nullable UUID createdBy);

  BannedWordDto updateBannedWord(UUID id, String word);

  void deleteBannedWord(UUID id);

  List<BannedWordDto> listBannedWords();

  Optional<BannedWordDto> findBannedWordById(UUID id);

  boolean containsBannedWord(String text);

  ChatReportDto reportMessage(UUID roomId, UUID messageId, UUID reporterId);

  ChatReportDto createReport(
      String messageText,
      UUID senderId,
      String senderNickname,
      UUID reporterId,
      List<ChatContextMessage> context);

  List<ChatReportDto> listReports(@Nullable ReportStatus status);

  Optional<ChatReportDto> findReportById(UUID id);

  ChatReportDto handleReport(UUID reportId, ReportStatus action, UUID handledBy);
}
