package com.meloo.chat.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "chat_report")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ChatReport implements Persistable<UUID> {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(name = "message_text", nullable = false, length = 200)
  private String messageText;

  @Column(name = "sender_id", nullable = false)
  private UUID senderId;

  @Column(name = "sender_nickname", nullable = false, length = 20)
  private String senderNickname;

  @Column(name = "reporter_id", nullable = false)
  private UUID reporterId;

  @Builder.Default
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false)
  private String context = "[]";

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ReportStatus status = ReportStatus.OPEN;

  @Nullable @Column(name = "handled_by")
  private UUID handledBy;

  @Nullable @Column(name = "handled_at")
  private Instant handledAt;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Transient
  @Builder.Default
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private boolean newEntity = true;

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    newEntity = false;
  }
}
