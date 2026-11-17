package com.meloo.progression.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Rank points for the requested identities; players without stats start at zero (BR-24). */
public interface RankPointsQuery {
  Map<UUID, Integer> findAll(Collection<UUID> userIds);
}
