package com.meloo.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AppUser {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private UserKind kind;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private UserRole role = UserRole.USER;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(nullable = false, length = 20)
  private String nickname;

  @Nullable @Column(length = 254)
  private String email;

  @Nullable @Column(name = "lock_reason")
  private String lockReason;

  @Nullable @Column(name = "locked_at")
  private Instant lockedAt;

  @Nullable @Column(name = "merged_into_id")
  private UUID mergedIntoId;

  @Column(name = "last_active_at", nullable = false)
  private Instant lastActiveAt;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (lastActiveAt == null) {
      lastActiveAt = now;
    }
  }
}
