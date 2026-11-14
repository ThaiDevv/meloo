package com.meloo.room.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomPlayerAvatarTest {
  @Test
  void presetAvatarIsPresentInRestAndRealtime() {
    var player =
        new RoomPlayer(
            UUID.randomUUID(), "Player", null, "piano", false, true, false, true, 0, Instant.now());
    assertThat(player.toDto().getAvatarId().getValue()).isEqualTo("piano");
    assertThat(player.toPayload().avatarId()).isEqualTo("piano");
  }
}
