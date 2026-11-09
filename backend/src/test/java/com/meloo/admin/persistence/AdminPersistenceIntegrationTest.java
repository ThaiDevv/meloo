package com.meloo.admin.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.meloo.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminPersistenceIntegrationTest extends IntegrationTest {
  @PersistenceContext private EntityManager entityManager;

  @Test
  void persistsIdentityIdsAndJsonPayloads() {
    UUID actor = UUID.randomUUID();
    var audit =
        AuditLog.builder()
            .actorId(actor)
            .action("USER_LOCK")
            .targetType("USER")
            .targetId(UUID.randomUUID().toString())
            .reason("spam")
            .detail("{\"source\":\"admin\"}")
            .build();
    var event =
        AnalyticsEvent.builder()
            .eventType("USER_REGISTERED")
            .userId(actor)
            .props("{\"source\":\"google\"}")
            .occurredAt(Instant.parse("2026-11-09T00:00:00Z"))
            .build();
    entityManager.persist(audit);
    entityManager.persist(event);
    entityManager.flush();
    entityManager.clear();
    assertThat(audit.getId()).isPositive();
    assertThat(event.getId()).isPositive();
    var loadedAudit = entityManager.find(AuditLog.class, audit.getId());
    assertThat(loadedAudit.getActorId()).isEqualTo(actor);
    assertThat(loadedAudit.getDetail()).contains("admin");
    assertThat(loadedAudit.getReason()).isEqualTo("spam");
    assertThat(loadedAudit.getCreatedAt()).isNotNull();
    var loadedEvent = entityManager.find(AnalyticsEvent.class, event.getId());
    assertThat(loadedEvent.getProps()).contains("google");
    assertThat(loadedEvent.getOccurredAt()).isEqualTo(Instant.parse("2026-11-09T00:00:00Z"));
  }
}
