package com.meloo.daily.service;

import com.meloo.daily.persistence.AttemptStatus;
import com.meloo.daily.persistence.DailyAttempt;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

public final class DailyStreaks {
  private DailyStreaks() {}

  public static int current(List<DailyAttempt> attempts, LocalDate today) {
    if (attempts.stream()
        .anyMatch(
            a -> a.getChallengeDate().equals(today) && a.getStatus() == AttemptStatus.UNSOLVED)) {
      return 0;
    }
    var solved =
        new HashSet<>(
            attempts.stream()
                .filter(a -> a.getStatus() == AttemptStatus.SOLVED)
                .map(DailyAttempt::getChallengeDate)
                .toList());
    LocalDate date = solved.contains(today) ? today : today.minusDays(1);
    int count = 0;
    while (solved.contains(date)) {
      count++;
      date = date.minusDays(1);
    }
    return count;
  }

  public static int longest(List<DailyAttempt> attempts, LocalDate from, LocalDate to) {
    var dates =
        attempts.stream()
            .filter(a -> a.getStatus() == AttemptStatus.SOLVED)
            .map(DailyAttempt::getChallengeDate)
            .filter(d -> !d.isBefore(from) && !d.isAfter(to))
            .distinct()
            .sorted()
            .toList();
    LocalDate previous = null;
    int count = 0;
    int longest = 0;
    for (LocalDate date : dates) {
      count = previous != null && previous.plusDays(1).equals(date) ? count + 1 : 1;
      longest = Math.max(longest, count);
      previous = date;
    }
    return longest;
  }
}
