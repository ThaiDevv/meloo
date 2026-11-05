package com.meloo.room.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.meloo.generated.model.RoomStatusDto;
import com.meloo.generated.model.RoomTypeDto;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;

class RoomSessionPromotionTest {
  @Test
  void promotesWithoutTouchingHostReadinessOrDisconnectTimer() {
    UUID id = UUID.randomUUID();
    UUID host = UUID.randomUUID();
    RoomSession room =
        new RoomSession(
            id,
            "ABC123",
            RoomTypeDto.PRIVATE_ROOM,
            host,
            RoomStatusDto.PLAYING,
            5,
            5,
            null,
            null,
            Instant.now());
    RoomPlayer player =
        new RoomPlayer(host, "Host", null, true, true, false, false, 0, Instant.now());
    room.addPlayer(player);
    ScheduledFuture<?> timer = mock(ScheduledFuture.class);
    room.addDisconnectTimer(host, timer);
    room.setStatus(RoomStatusDto.LOBBY);
    room.promoteWaitingPlayers();
    assertThat(room.players()).containsExactly(player);
    assertThat(room.waitingPlayers()).isEmpty();
    assertThat(room.hostId()).isEqualTo(host);
    assertThat(player.isHost()).isTrue();
    assertThat(player.isReady()).isTrue();
    assertThat(player.isConnected()).isFalse();
    verifyNoInteractions(timer);
    room.cancelDisconnectTimer(host);
    verify(timer).cancel(false);
  }
}
