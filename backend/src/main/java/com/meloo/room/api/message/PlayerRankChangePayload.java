package com.meloo.room.api.message;

import java.util.UUID;

/** Rank delta and updated rank points for a participant in a QUICK_MATCH (BR-24). */
public record PlayerRankChangePayload(UUID userId, int rankDelta, int rankPoints) {}
