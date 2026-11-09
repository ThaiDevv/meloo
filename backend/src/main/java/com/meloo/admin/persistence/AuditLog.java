package com.meloo.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@org.hibernate.annotations.Immutable
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Nullable @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "action", nullable = false, length = 40)
  private String action;

  @Column(name = "target_type", nullable = false, length = 30)
  private String targetType;

  @Nullable @Column(name = "target_id", nullable = true, length = 64)
  private String targetId;

  @Nullable @Column(name = "reason", nullable = true, columnDefinition = "text")
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Nullable @Column(name = "detail", nullable = true)
  private String detail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
