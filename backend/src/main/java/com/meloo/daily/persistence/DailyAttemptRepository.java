package com.meloo.daily.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DailyAttemptRepository extends JpaRepository<DailyAttempt, UUID> {
  Optional<DailyAttempt> findByUserIdAndChallengeDate(UUID userId, LocalDate date);

  List<DailyAttempt> findByUserIdOrderByChallengeDateAsc(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<DailyAttempt> findByUserIdAndStatusOrderByStartedAtAsc(UUID userId, AttemptStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from DailyAttempt a where a.id = :id")
  Optional<DailyAttempt> findLockedById(UUID id);

  @Query("select a.userId from DailyAttempt a where a.id = :id")
  Optional<UUID> findOwner(UUID id);

  @Query(
      "select a.id from DailyAttempt a where a.status = 'IN_PROGRESS' and a.startedAt <= :cutoff order by a.startedAt limit 500")
  List<UUID> findExpired(Instant cutoff);

  @Query(
      "select a.userId from DailyAttempt a where a.challengeDate = :date and a.guest = false and a.status <> 'IN_PROGRESS' and a.userId is not null")
  List<UUID> findLeaderboardUsers(LocalDate date);
}
