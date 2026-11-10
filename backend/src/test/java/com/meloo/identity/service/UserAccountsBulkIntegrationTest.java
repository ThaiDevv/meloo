package com.meloo.identity.service;

import static org.assertj.core.api.Assertions.*;

import com.meloo.IntegrationTest;
import com.meloo.identity.api.UserAccounts;
import com.meloo.identity.persistence.*;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserAccountsBulkIntegrationTest extends IntegrationTest {
  @Autowired UserAccounts accounts;
  @Autowired AppUserRepository users;
  @Autowired EntityManagerFactory emf;

  @Test
  void bulkLookupReturnsOnlyActiveRegisteredUnmergedAccountsWithoutNPlusOne() {
    UUID active = user(UserKind.REGISTERED, UserStatus.ACTIVE, null);
    UUID locked = user(UserKind.REGISTERED, UserStatus.LOCKED, null);
    UUID guest = user(UserKind.GUEST, UserStatus.ACTIVE, null);
    UUID merged = user(UserKind.REGISTERED, UserStatus.ACTIVE, active);
    users.flush();
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.setStatisticsEnabled(true);
    long before = stats.getPrepareStatementCount();
    var result = accounts.findByIdIn(List.of(active, locked, guest, merged, UUID.randomUUID()));
    assertThat(result).containsOnlyKeys(active);
    assertThat(result.get(active).nickname()).isEqualTo("BulkPlayer");
    assertThat(stats.getPrepareStatementCount() - before).isEqualTo(2);
    assertThat(accounts.findByIdIn(List.of())).isEmpty();
  }

  @Test
  void chunksLargeInputsWithoutDroppingAccountsOrDuplicatingIds() {
    var ids = new java.util.ArrayList<UUID>();
    for (int i = 0; i < 1001; i++) {
      ids.add(user(UserKind.REGISTERED, UserStatus.ACTIVE, null));
    }
    users.flush();
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    long before = statistics.getPrepareStatementCount();
    ids.add(ids.getFirst());
    assertThat(accounts.findByIdIn(ids)).hasSize(1001).containsKeys(ids.toArray(UUID[]::new));
    assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(4);
  }

  @Test
  void nicknameApiNormalizesUnicodeAndEnforcesRegisteredActiveAccounts() {
    UUID registered = user(UserKind.REGISTERED, UserStatus.ACTIVE, null);
    UUID guest = user(UserKind.GUEST, UserStatus.ACTIVE, null);
    UUID locked = user(UserKind.REGISTERED, UserStatus.LOCKED, null);
    assertThat(accounts.updateNickname(registered, "  Cafe\u0301 Player  ").nickname())
        .isEqualTo("Café Player");
    assertThatThrownBy(() -> accounts.updateNickname(guest, "New Player"))
        .isInstanceOf(com.meloo.shared.api.ApiException.class)
        .hasMessageContaining("read-only");
    assertThatThrownBy(() -> accounts.updateNickname(locked, "New Player"))
        .isInstanceOf(com.meloo.shared.api.ApiException.class)
        .hasMessageContaining("locked");
    assertThatThrownBy(() -> accounts.updateNickname(registered, "địt"))
        .isInstanceOf(com.meloo.shared.api.ApiException.class)
        .hasMessageContaining("inappropriate");
  }

  private UUID user(UserKind kind, UserStatus status, UUID mergedInto) {
    UUID id = UUID.randomUUID();
    users.save(
        AppUser.builder()
            .id(id)
            .kind(kind)
            .nickname("BulkPlayer")
            .status(status)
            .lockReason(status == UserStatus.LOCKED ? "Test lock" : null)
            .mergedIntoId(mergedInto)
            .build());
    return id;
  }
}
