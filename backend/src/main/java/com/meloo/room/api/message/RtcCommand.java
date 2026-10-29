package com.meloo.room.api.message;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Client-to-server WebRTC voice signaling (S5). */
public record RtcCommand(@Nullable UUID targetUserId, String signalType, String payload) {}
