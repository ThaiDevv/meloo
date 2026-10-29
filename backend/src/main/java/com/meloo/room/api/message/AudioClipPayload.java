package com.meloo.room.api.message;

import java.time.Instant;

/** Opaque audio preview stream URL with signature (ADR-008). No metadata leaked. */
public record AudioClipPayload(String url, int level, Instant expiresAt) {}
