package com.meloo.chat.service;

import com.meloo.chat.api.BannedWordDto;
import com.meloo.chat.api.ChatAdminApi;
import com.meloo.chat.api.ChatContextMessage;
import com.meloo.chat.api.ChatReportDto;
import com.meloo.chat.api.ReportStatus;
import com.meloo.chat.persistence.BannedWord;
import com.meloo.chat.persistence.BannedWordRepository;
import com.meloo.chat.persistence.ChatReport;
import com.meloo.chat.persistence.ChatReportRepository;
import com.meloo.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ChatAdminServiceImpl implements ChatAdminApi {

  private static final TypeReference<List<ChatContextMessage>> CONTEXT_TYPE_REF =
      new TypeReference<>() {};

  private final BannedWordRepository bannedWordRepository;
  private final ChatReportRepository chatReportRepository;
  private final BannedWordService bannedWordService;
  private final ChatHistoryManager historyManager;
  private final ObjectMapper objectMapper;

  public ChatAdminServiceImpl(
      BannedWordRepository bannedWordRepository,
      ChatReportRepository chatReportRepository,
      BannedWordService bannedWordService,
      ChatHistoryManager historyManager,
      ObjectMapper objectMapper) {
    this.bannedWordRepository =
        Objects.requireNonNull(bannedWordRepository, "bannedWordRepository");
    this.chatReportRepository =
        Objects.requireNonNull(chatReportRepository, "chatReportRepository");
    this.bannedWordService = Objects.requireNonNull(bannedWordService, "bannedWordService");
    this.historyManager = Objects.requireNonNull(historyManager, "historyManager");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public BannedWordDto addBannedWord(String word, @Nullable UUID createdBy) {
    if (word == null || word.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_WORD", "Banned word must not be blank");
    }
    String normalized = word.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() > 80) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_WORD", "Banned word must not exceed 80 characters");
    }

    UUID id = UUID.randomUUID();
    BannedWord entity =
        BannedWord.builder()
            .id(id)
            .word(normalized)
            .createdBy(createdBy)
            .createdAt(Instant.now())
            .build();
    BannedWord saved = bannedWordRepository.save(entity);
    bannedWordService.invalidateCache();
    return toDto(saved);
  }

  @Override
  public BannedWordDto updateBannedWord(UUID id, String word) {
    Objects.requireNonNull(id, "id");
    if (word == null || word.isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_WORD", "Banned word must not be blank");
    }
    String normalized = word.trim().toLowerCase(Locale.ROOT);
    if (normalized.length() > 80) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_WORD", "Banned word must not exceed 80 characters");
    }

    BannedWord entity =
        bannedWordRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "WORD_NOT_FOUND", "Banned word not found"));

    entity.setWord(normalized);
    BannedWord saved = bannedWordRepository.save(entity);
    bannedWordService.invalidateCache();
    return toDto(saved);
  }

  @Override
  public void deleteBannedWord(UUID id) {
    Objects.requireNonNull(id, "id");
    if (!bannedWordRepository.existsById(id)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "WORD_NOT_FOUND", "Banned word not found");
    }
    bannedWordRepository.deleteById(id);
    bannedWordService.invalidateCache();
  }

  @Override
  @Transactional(readOnly = true)
  public List<BannedWordDto> listBannedWords() {
    return bannedWordRepository.findAll().stream().map(this::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BannedWordDto> findBannedWordById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return bannedWordRepository.findById(id).map(this::toDto);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean containsBannedWord(String text) {
    return bannedWordService.containsBannedWord(text);
  }

  @Override
  public ChatReportDto reportMessage(UUID roomId, UUID messageId, UUID reporterId) {
    Objects.requireNonNull(roomId, "roomId");
    Objects.requireNonNull(messageId, "messageId");
    Objects.requireNonNull(reporterId, "reporterId");

    ChatContextMessage reportedMsg =
        historyManager
            .findMessage(roomId, messageId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        "MESSAGE_NOT_FOUND",
                        "Reported message not found in room history"));

    List<ChatContextMessage> preceding = historyManager.findPrecedingContext(roomId, messageId, 5);
    return createReport(
        reportedMsg.content(),
        reportedMsg.senderId(),
        reportedMsg.senderNickname(),
        reporterId,
        preceding);
  }

  @Override
  public ChatReportDto createReport(
      String messageText,
      UUID senderId,
      String senderNickname,
      UUID reporterId,
      List<ChatContextMessage> context) {
    Objects.requireNonNull(messageText, "messageText");
    Objects.requireNonNull(senderId, "senderId");
    Objects.requireNonNull(senderNickname, "senderNickname");
    Objects.requireNonNull(reporterId, "reporterId");

    List<ChatContextMessage> safeContext = context != null ? List.copyOf(context) : List.of();
    String contextJson = serializeContext(safeContext);

    UUID id = UUID.randomUUID();
    ChatReport report =
        ChatReport.builder()
            .id(id)
            .messageText(messageText)
            .senderId(senderId)
            .senderNickname(senderNickname)
            .reporterId(reporterId)
            .context(contextJson)
            .status(ReportStatus.OPEN)
            .createdAt(Instant.now())
            .build();

    ChatReport saved = chatReportRepository.save(report);
    return toDto(saved, safeContext);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatReportDto> listReports(@Nullable ReportStatus status) {
    List<ChatReport> reports =
        status != null
            ? chatReportRepository.findByStatusOrderByCreatedAtDesc(status)
            : chatReportRepository.findAllByOrderByCreatedAtDesc();
    return reports.stream().map(this::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ChatReportDto> findReportById(UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return chatReportRepository.findById(id).map(this::toDto);
  }

  @Override
  public ChatReportDto handleReport(UUID reportId, ReportStatus action, UUID handledBy) {
    Objects.requireNonNull(reportId, "reportId");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(handledBy, "handledBy");

    if (action == ReportStatus.OPEN) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_ACTION", "Report action must be DISMISSED or ACTIONED");
    }

    ChatReport report =
        chatReportRepository
            .findById(reportId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Chat report not found"));

    report.setStatus(action);
    report.setHandledBy(handledBy);
    report.setHandledAt(Instant.now());

    ChatReport saved = chatReportRepository.save(report);
    return toDto(saved);
  }

  private BannedWordDto toDto(BannedWord entity) {
    return new BannedWordDto(
        entity.getId(), entity.getWord(), entity.getCreatedBy(), entity.getCreatedAt());
  }

  private ChatReportDto toDto(ChatReport entity) {
    List<ChatContextMessage> context = deserializeContext(entity.getContext());
    return toDto(entity, context);
  }

  private ChatReportDto toDto(ChatReport entity, List<ChatContextMessage> context) {
    return new ChatReportDto(
        entity.getId(),
        entity.getMessageText(),
        entity.getSenderId(),
        entity.getSenderNickname(),
        entity.getReporterId(),
        context,
        entity.getStatus(),
        entity.getHandledBy(),
        entity.getHandledAt(),
        entity.getCreatedAt());
  }

  private String serializeContext(List<ChatContextMessage> context) {
    try {
      return objectMapper.writeValueAsString(context);
    } catch (Exception e) {
      return "[]";
    }
  }

  private List<ChatContextMessage> deserializeContext(String json) {
    if (json == null || json.isBlank() || json.equals("[]")) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, CONTEXT_TYPE_REF);
    } catch (Exception e) {
      return List.of();
    }
  }
}
