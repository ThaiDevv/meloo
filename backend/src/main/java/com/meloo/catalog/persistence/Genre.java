package com.meloo.catalog.persistence;

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

@Entity
@Table(name = "genre")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Genre {

  @Id
  @Column(updatable = false)
  private UUID id;

  @Column(nullable = false, length = 40, unique = true)
  private String code;

  @Column(name = "name_vi", nullable = false, length = 80)
  private String nameVi;

  @Column(name = "name_en", nullable = false, length = 80)
  private String nameEn;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;
}
