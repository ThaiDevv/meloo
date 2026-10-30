package com.meloo.room.api;

import java.util.Optional;
import java.util.UUID;

/** Internal query used by chat moderation; this answer must never be sent to clients. */
public interface ActiveRoundQuery {

  Optional<String> findActiveAnswerTitle(UUID roomId);
}
