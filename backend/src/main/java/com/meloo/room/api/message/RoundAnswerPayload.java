package com.meloo.room.api.message;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** True track information revealed strictly at ROUND_END (BR-29). */
public record RoundAnswerPayload(
    UUID trackId,
    String trackTitle,
    String artistName,
    @Nullable String albumTitle,
    @Nullable String coverUrl,
    String previewFullUrl) {}
