package com.meloo.daily.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyChallengeRepository extends JpaRepository<DailyChallenge, LocalDate> {
  List<DailyChallenge> findByChallengeDateBetweenOrderByChallengeDateAsc(
      LocalDate from, LocalDate to);
}
