package com.meloo.chat.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "banned_word")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BannedWord {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(nullable = false, length = 80, unique = true)
  private String word;

  @Nullable @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;
}
