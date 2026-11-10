package com.meloo.admin.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("meloo.admin")
public record AdminProperties(
    @Min(1) int retentionDays,
    @NotBlank String retentionCron,
    @Nullable LocalDate betaStart,
    LocalDate betaEnd,
    LocalDate betaCutoff,
    Set<UUID> excludedUserIds) {
  public AdminProperties {
    excludedUserIds = excludedUserIds == null ? Set.of() : Set.copyOf(excludedUserIds);
  }

  @AssertTrue(message = "Beta dates need start <= cutoff and at least two days before end") public boolean isBetaWindowValid() {
    return betaEnd != null
        && betaCutoff != null
        && !betaCutoff.isAfter(betaEnd.minusDays(2))
        && (betaStart == null || !betaStart.isAfter(betaCutoff));
  }
}
