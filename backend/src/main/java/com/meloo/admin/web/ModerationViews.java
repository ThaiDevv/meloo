package com.meloo.admin.web;

import com.meloo.chat.api.BannedWordDto;
import com.meloo.chat.api.ChatReportDto;
import com.meloo.generated.model.*;
import com.meloo.identity.api.UserAccount;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

final class ModerationViews {
  private ModerationViews() {}

  static AdminUserDto user(UserAccount user, int rankPoints) {
    return new AdminUserDto(
            user.id(),
            UserKindDto.fromValue(user.kind()),
            UserRoleDto.fromValue(user.role()),
            UserAccountStatusDto.fromValue(user.status()),
            user.nickname(),
            rankPoints,
            user.linkedProviders().stream().map(OAuthProviderDto::fromValue).toList(),
            at(user.lastActiveAt()),
            at(user.createdAt()))
        .email(user.email())
        .lockReason(user.lockReason())
        .lockedAt(at(user.lockedAt()))
        .avatarId(
            user.avatarId() == null ? null : AdminUserDto.AvatarIdEnum.fromValue(user.avatarId()))
        .profileCompleted(user.profileCompleted());
  }

  static AdminChatReportDto report(ChatReportDto report) {
    return new AdminChatReportDto(
            report.id(),
            report.messageText(),
            report.senderId(),
            report.senderNickname(),
            report.reporterId(),
            report.context().stream()
                .map(
                    c ->
                        new AdminChatContextMessageDto(
                            c.messageId(),
                            c.senderId(),
                            c.senderNickname(),
                            c.content(),
                            at(c.sentAt())))
                .toList(),
            ChatReportStatusDto.fromValue(report.status().name()),
            at(report.createdAt()))
        .handledBy(report.handledBy())
        .handledAt(at(report.handledAt()));
  }

  static AdminBannedWordDto word(BannedWordDto word) {
    return new AdminBannedWordDto(word.id(), word.word(), at(word.createdAt()))
        .createdBy(word.createdBy());
  }

  private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
