package com.meloo.identity.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken r set r.revokedAt = :now where r.userId = :userId and r.revokedAt is null")
  int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying(clearAutomatically = true)
  @Query(
      "update RefreshToken r set r.revokedAt = :revokedAt where r.familyId = :familyId and r.revokedAt is null")
  int revokeAllInFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}
