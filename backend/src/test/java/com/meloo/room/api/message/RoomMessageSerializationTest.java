package com.meloo.room.api.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RoomMessageSerializationTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Nested
  @DisplayName("Client Commands Serialization Tests")
  class ClientCommandsTests {

    @Test
    @DisplayName("ReadyCommand serialization round-trip")
    void testReadyCommand() throws Exception {
      ReadyCommand cmd = new ReadyCommand(true);
      String json = objectMapper.writeValueAsString(cmd);
      ReadyCommand deserialized = objectMapper.readValue(json, ReadyCommand.class);
      assertThat(deserialized.ready()).isTrue();
    }

    @Test
    @DisplayName("SettingsCommand serialization round-trip")
    void testSettingsCommand() throws Exception {
      UUID playlistId = UUID.randomUUID();
      SettingsCommand cmd = new SettingsCommand(10, 5, playlistId);
      String json = objectMapper.writeValueAsString(cmd);
      SettingsCommand deserialized = objectMapper.readValue(json, SettingsCommand.class);
      assertThat(deserialized.roundCount()).isEqualTo(10);
      assertThat(deserialized.maxPlayers()).isEqualTo(5);
      assertThat(deserialized.playlistId()).isEqualTo(playlistId);
    }

    @Test
    @DisplayName("KickCommand serialization round-trip")
    void testKickCommand() throws Exception {
      UUID target = UUID.randomUUID();
      KickCommand cmd = new KickCommand(target);
      String json = objectMapper.writeValueAsString(cmd);
      KickCommand deserialized = objectMapper.readValue(json, KickCommand.class);
      assertThat(deserialized.targetUserId()).isEqualTo(target);
    }

    @Test
    @DisplayName("Commands with roundNo round-trip")
    void testRoundCommands() throws Exception {
      PreparedCommand prep = new PreparedCommand(2);
      assertThat(
              objectMapper
                  .readValue(objectMapper.writeValueAsString(prep), PreparedCommand.class)
                  .roundNo())
          .isEqualTo(2);

      ListenMoreCommand lm = new ListenMoreCommand(2, 5);
      ListenMoreCommand lmDeser =
          objectMapper.readValue(objectMapper.writeValueAsString(lm), ListenMoreCommand.class);
      assertThat(lmDeser.roundNo()).isEqualTo(2);
      assertThat(lmDeser.currentLevel()).isEqualTo(5);

      GiveUpCommand gu = new GiveUpCommand(3);
      assertThat(
              objectMapper
                  .readValue(objectMapper.writeValueAsString(gu), GiveUpCommand.class)
                  .roundNo())
          .isEqualTo(3);

      UUID trackId = UUID.randomUUID();
      GuessCommand gc = new GuessCommand(3, trackId);
      GuessCommand gcDeser =
          objectMapper.readValue(objectMapper.writeValueAsString(gc), GuessCommand.class);
      assertThat(gcDeser.roundNo()).isEqualTo(3);
      assertThat(gcDeser.trackId()).isEqualTo(trackId);

      ChatCommand cc = new ChatCommand("Hay quá!");
      assertThat(
              objectMapper
                  .readValue(objectMapper.writeValueAsString(cc), ChatCommand.class)
                  .content())
          .isEqualTo("Hay quá!");
    }
  }

  @Nested
  @DisplayName("Lobby Events Serialization & AC Tests")
  class LobbyEventsTests {

    @Test
    @DisplayName("PlayerJoinedEvent includes user and serverTime")
    void testPlayerJoinedEvent() throws Exception {
      Instant now = Instant.parse("2026-10-29T10:00:00Z");
      RoomPlayerSummaryPayload player =
          new RoomPlayerSummaryPayload(UUID.randomUUID(), "PlayerOne", null, true, true, false, 0);
      PlayerJoinedEvent event = new PlayerJoinedEvent(player, now);

      String json = objectMapper.writeValueAsString(event);
      PlayerJoinedEvent deserialized = objectMapper.readValue(json, PlayerJoinedEvent.class);

      assertThat(deserialized.type()).isEqualTo("PLAYER_JOINED");
      assertThat(deserialized.user().nickname()).isEqualTo("PlayerOne");
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("MatchCountdownEvent (BR-13) 30s countdown")
    void testMatchCountdown() throws Exception {
      Instant now = Instant.now();
      MatchCountdownEvent event = new MatchCountdownEvent(30, now);
      String json = objectMapper.writeValueAsString(event);
      MatchCountdownEvent deserialized = objectMapper.readValue(json, MatchCountdownEvent.class);

      assertThat(deserialized.type()).isEqualTo("MATCH_COUNTDOWN");
      assertThat(deserialized.remainingSeconds()).isEqualTo(30);
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("MatchSoloSuggestionEvent (BR-13) after 60s waiting alone")
    void testSoloSuggestion() throws Exception {
      Instant now = Instant.now();
      MatchSoloSuggestionEvent event = new MatchSoloSuggestionEvent("Chơi đơn?", now);
      String json = objectMapper.writeValueAsString(event);
      MatchSoloSuggestionEvent deserialized =
          objectMapper.readValue(json, MatchSoloSuggestionEvent.class);

      assertThat(deserialized.type()).isEqualTo("MATCH_SOLO_SUGGESTION");
      assertThat(deserialized.message()).isEqualTo("Chơi đơn?");
    }

    @Test
    @DisplayName("WaitingListChangedEvent (BR-15) and ReturnToLobbyEvent (BR-12)")
    void testWaitingListAndReturnToLobby() throws Exception {
      Instant now = Instant.now();
      WaitingListChangedEvent wlEvent =
          new WaitingListChangedEvent(
              List.of(
                  new RoomPlayerSummaryPayload(
                      UUID.randomUUID(), "LateUser", null, false, false, true, 0)),
              now);
      WaitingListChangedEvent wlDeser =
          objectMapper.readValue(
              objectMapper.writeValueAsString(wlEvent), WaitingListChangedEvent.class);
      assertThat(wlDeser.waitingPlayers()).hasSize(1);

      ReturnToLobbyEvent rtl = new ReturnToLobbyEvent(now);
      ReturnToLobbyEvent rtlDeser =
          objectMapper.readValue(objectMapper.writeValueAsString(rtl), ReturnToLobbyEvent.class);
      assertThat(rtlDeser.type()).isEqualTo("RETURN_TO_LOBBY");
      assertThat(rtlDeser.serverTime()).isEqualTo(now);
    }
  }

  @Nested
  @DisplayName("Fairness & Anti-Leak Tests (AC 3, AC 4)")
  class FairnessAndAntiCheatTests {

    @Test
    @DisplayName("Active round events NEVER leak trackTitle or artistName (AC 4, NFR-07)")
    void activeRoundEventsDoNotLeakAnswers() {
      Class<?>[] activePayloadClasses = {
        RoundPrepareEvent.class,
        RoundStartEvent.class,
        ListenMoreOpenedEvent.class,
        PlayerGaveUpEvent.class,
        PlayerGuessedEvent.class,
        RoundTrackReplacedEvent.class,
        ActiveRoundStatePayload.class,
        AudioClipPayload.class
      };

      for (Class<?> clazz : activePayloadClasses) {
        for (Field field : clazz.getDeclaredFields()) {
          String fieldName = field.getName().toLowerCase();
          assertThat(fieldName)
              .withFailMessage(
                  "Class %s leaks answer metadata through field '%s'",
                  clazz.getSimpleName(), field.getName())
              .doesNotContain("tracktitle")
              .doesNotContain("artistname");
        }
      }
    }

    @Test
    @DisplayName(
        "PlayerGuessedEvent (BR-07) broadcasts '<name> guessed at level Xs' without answer")
    void playerGuessedEventDoesNotContainAnswer() throws Exception {
      Instant now = Instant.now();
      UUID userId = UUID.randomUUID();
      PlayerGuessedEvent event = new PlayerGuessedEvent(userId, "ThaiPO", 5, true, now);

      String json = objectMapper.writeValueAsString(event);
      assertThat(json).doesNotContain("trackId");
      assertThat(json).doesNotContain("trackTitle");
      assertThat(json).doesNotContain("artistName");

      PlayerGuessedEvent deserialized = objectMapper.readValue(json, PlayerGuessedEvent.class);
      assertThat(deserialized.nickname()).isEqualTo("ThaiPO");
      assertThat(deserialized.level()).isEqualTo(5);
      assertThat(deserialized.isCorrect()).isTrue();
    }

    @Test
    @DisplayName(
        "RoundEndEvent reveals answer and detailed score breakdown (BR-02, BR-07, BR-08, BR-29)")
    void roundEndEventContainsAnswerAndScoreDetail() throws Exception {
      Instant now = Instant.now();
      UUID trackId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();

      RoundAnswerPayload answer =
          new RoundAnswerPayload(
              trackId,
              "Cắt Đôi Nỗi Sầu",
              "Tăng Duy Tân",
              "Single",
              "https://cover.url",
              "https://www.deezer.com/track/12345678");

      PlayerRoundResultPayload result =
          new PlayerRoundResultPayload(userId, "CORRECT", 5, 800, 10, 133, 1013, 0, 15000L);

      RoundEndEvent event = new RoundEndEvent(1, answer, List.of(result), null, now);
      String json = objectMapper.writeValueAsString(event);

      RoundEndEvent deserialized = objectMapper.readValue(json, RoundEndEvent.class);
      assertThat(deserialized.type()).isEqualTo("ROUND_END");
      assertThat(deserialized.roundNo()).isEqualTo(1);
      assertThat(deserialized.answer().trackTitle()).isEqualTo("Cắt Đôi Nỗi Sầu");
      assertThat(deserialized.answer().previewFullUrl())
          .isEqualTo("https://www.deezer.com/track/12345678");

      PlayerRoundResultPayload playerResult = deserialized.results().getFirst();
      assertThat(playerResult.baseScore()).isEqualTo(800);
      assertThat(playerResult.comboPercent()).isEqualTo(10);
      assertThat(playerResult.speedBonus()).isEqualTo(133);
      assertThat(playerResult.roundScore()).isEqualTo(1013);
      assertThat(playerResult.answerTimeMs()).isEqualTo(15000L);
    }

    @Test
    @DisplayName("MatchEarlyEndedEvent (BR-14) when only 1 player remains")
    void matchEarlyEndedEvent() throws Exception {
      Instant now = Instant.now();
      UUID survivor = UUID.randomUUID();
      MatchEarlyEndedEvent event = new MatchEarlyEndedEvent("OPPONENT_LEFT", survivor, now);

      String json = objectMapper.writeValueAsString(event);
      MatchEarlyEndedEvent deserialized = objectMapper.readValue(json, MatchEarlyEndedEvent.class);

      assertThat(deserialized.type()).isEqualTo("MATCH_EARLY_ENDED");
      assertThat(deserialized.remainingUserId()).isEqualTo(survivor);
    }
  }

  @Nested
  @DisplayName("Scoreboard & Tie-Break Tests (AC 3, BR-10)")
  class ScoreboardTests {

    @Test
    @DisplayName("MatchFinishedEvent includes tie-break milliseconds (BR-10)")
    void matchFinishedTieBreakMs() throws Exception {
      Instant now = Instant.now();
      UUID matchId = UUID.randomUUID();
      UUID user1 = UUID.randomUUID();
      UUID user2 = UUID.randomUUID();

      List<PlayerRankingPayload> rankings =
          List.of(
              new PlayerRankingPayload(1, user1, "FastUser", 8000, 8, 42000L),
              new PlayerRankingPayload(2, user2, "SlowUser", 8000, 8, 56000L));

      MatchFinishedEvent event = new MatchFinishedEvent(matchId, rankings, now);
      String json = objectMapper.writeValueAsString(event);
      MatchFinishedEvent deserialized = objectMapper.readValue(json, MatchFinishedEvent.class);

      assertThat(deserialized.type()).isEqualTo("MATCH_FINISHED");
      assertThat(deserialized.rankings()).hasSize(2);
      assertThat(deserialized.rankings().get(0).totalCorrectTimeMs()).isEqualTo(42000L);
      assertThat(deserialized.rankings().get(1).totalCorrectTimeMs()).isEqualTo(56000L);
    }
  }

  @Nested
  @DisplayName("Private Queue & Resync Tests (BR-14, BR-16, BR-17)")
  class PrivateQueueTests {

    @Test
    @DisplayName("ErrorMessage supports blocked chat notification (BR-16, BR-17)")
    void errorMessageBlockedChat() throws Exception {
      Instant now = Instant.now();
      ErrorMessage err =
          new ErrorMessage(
              "CHAT_MESSAGE_BLOCKED",
              "Tin nhắn bị chặn do vi phạm quy tắc phòng (BR-16)",
              "Chứa tên bài hát trong vòng chơi",
              now);

      String json = objectMapper.writeValueAsString(err);
      ErrorMessage deserialized = objectMapper.readValue(json, ErrorMessage.class);

      assertThat(deserialized.type()).isEqualTo("ERROR");
      assertThat(deserialized.code()).isEqualTo("CHAT_MESSAGE_BLOCKED");
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("ResyncStateMessage (BR-14) delivers full room snapshot")
    void resyncStateMessageSnapshot() throws Exception {
      Instant now = Instant.now();
      UUID roomId = UUID.randomUUID();
      UUID hostId = UUID.randomUUID();

      RoomPlayerSummaryPayload host =
          new RoomPlayerSummaryPayload(hostId, "HostUser", null, true, true, false, 1000);

      RoomResponsePayload room =
          new RoomResponsePayload(
              roomId,
              "MEL889",
              "PRIVATE_ROOM",
              hostId,
              "PLAYING",
              10,
              5,
              2,
              1,
              List.of(host),
              null,
              now);

      ActiveRoundStatePayload currentRound =
          new ActiveRoundStatePayload(
              2,
              10,
              2,
              new AudioClipPayload("/api/v1/audio/clips/token", 2, now.plusSeconds(300)),
              35000,
              false);

      ResyncStateMessage resync =
          new ResyncStateMessage(
              room,
              "ROUND_IN_PROGRESS",
              currentRound,
              List.of(new PlayerScoreSummaryPayload(hostId, "HostUser", 1000, 1, 1)),
              now);

      String json = objectMapper.writeValueAsString(resync);
      ResyncStateMessage deserialized = objectMapper.readValue(json, ResyncStateMessage.class);

      assertThat(deserialized.type()).isEqualTo("RESYNC_STATE");
      assertThat(deserialized.room().code()).isEqualTo("MEL889");
      assertThat(deserialized.roomPhase()).isEqualTo("ROUND_IN_PROGRESS");
      assertThat(deserialized.currentRound()).isNotNull();
      assertThat(deserialized.currentRound().roundNo()).isEqualTo(2);
      assertThat(deserialized.currentRound().remainingMs()).isEqualTo(35000);
    }
  }
}
