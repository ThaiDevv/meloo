package com.meloo.identity.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select u from AppUser u where u.id = (select r.userId from RefreshToken r where r.tokenHash = :hash)")
  Optional<AppUser> lockRefreshOwner(@Param("hash") String hash);

  Optional<AppUser> findByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from AppUser u where u.id = :id")
  Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      "select u from AppUser u where u.mergedIntoId is null and "
          + "(lower(u.nickname) like lower(concat('%', :search, '%')) "
          + "or lower(u.email) like lower(concat('%', :search, '%')))")
  Page<AppUser> search(@Param("search") String search, Pageable pageable);

  @Query(
      "select count(u) from AppUser u where u.mergedIntoId is null and u.kind = :kind "
          + "and u.createdAt >= :from and u.createdAt < :to")
  long countCreated(
      @Param("kind") UserKind kind, @Param("from") Instant from, @Param("to") Instant to);

  boolean existsByIdAndMergedIntoIdIsNotNull(UUID id);

  /**
   * Checks whether a user exists with the specified ID and status.
   *
   * @param id the user ID
   * @param status the user status
   * @return {@code true} if a record exists matching both ID and status, {@code false} otherwise
   */
  boolean existsByIdAndStatus(UUID id, UserStatus status);
}
