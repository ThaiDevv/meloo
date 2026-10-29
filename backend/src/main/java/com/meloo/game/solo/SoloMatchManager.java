package com.meloo.game.solo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Quản lý lưu trữ phiên chơi đơn in-memory (ConcurrentHashMap). */
@Component
public class SoloMatchManager {

  private final Map<UUID, SoloMatch> matchesById = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> activeMatchIdByUserId = new ConcurrentHashMap<>();
  private static final int STRIPE_COUNT = 256;
  private final Object[] userLocks = new Object[STRIPE_COUNT];

  public SoloMatchManager() {
    for (int i = 0; i < STRIPE_COUNT; i++) {
      userLocks[i] = new Object();
    }
  }

  public Object getUserLock(UUID userId) {
    int index = Math.floorMod(userId.hashCode(), STRIPE_COUNT);
    return userLocks[index];
  }

  public void registerMatch(SoloMatch match) {
    matchesById.put(match.matchId(), match);
    activeMatchIdByUserId.put(match.userId(), match.matchId());
  }

  public Optional<SoloMatch> getMatchById(UUID matchId) {
    return Optional.ofNullable(matchesById.get(matchId));
  }

  public Optional<SoloMatch> getActiveMatchByUserId(UUID userId) {
    UUID matchId = activeMatchIdByUserId.get(userId);
    if (matchId == null) {
      return Optional.empty();
    }
    SoloMatch match = matchesById.get(matchId);
    if (match == null || match.status() != SoloMatchStatus.IN_PROGRESS) {
      activeMatchIdByUserId.remove(userId, matchId);
      return Optional.empty();
    }
    return Optional.of(match);
  }

  public void clearActiveMatch(UUID userId, UUID matchId) {
    activeMatchIdByUserId.remove(userId, matchId);
  }

  public List<SoloMatch> getAllMatches() {
    return new ArrayList<>(matchesById.values());
  }

  public void purgeOldMatches(Instant cutoffTime) {
    matchesById
        .entrySet()
        .removeIf(
            entry -> {
              SoloMatch m = entry.getValue();
              return m.status() != SoloMatchStatus.IN_PROGRESS
                  && m.isMatchFinishedPublished()
                  && m.endedAt() != null
                  && m.endedAt().isBefore(cutoffTime);
            });
  }
}
