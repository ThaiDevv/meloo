package com.meloo.game.solo;

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
    if (match == null) {
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
}
