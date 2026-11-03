package com.meloo.room.api.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RoomMessageSerializationTest {

  private JsonMapper jsonMapper;

  @BeforeEach
  void setUp() {
    jsonMapper = JsonMapper.builder().build();
  }

  @Nested
  @DisplayName("Client Commands Serialization Tests")
  class ClientCommandsTests {

    @Test
    @DisplayName("ReadyCommand serialization round-trip")
    void testReadyCommand() throws Exception {
      ReadyCommand cmd = new ReadyCommand(true);
      String json = jsonMapper.writeValueAsString(cmd);
      ReadyCommand deserialized = jsonMapper.readValue(json, ReadyCommand.class);
      assertThat(deserialized.ready()).isTrue();
    }

    @Test
    @DisplayName("SettingsCommand serialization round-trip")
    void testSettingsCommand() throws Exception {
      UUID playlistId = UUID.randomUUID();
      SettingsCommand cmd = new SettingsCommand(10, 5, playlistId);
      String json = jsonMapper.writeValueAsString(cmd);
      SettingsCommand deserialized = jsonMapper.readValue(json, SettingsCommand.class);
      assertThat(deserialized.roundCount()).isEqualTo(10);
      assertThat(deserialized.maxPlayers()).isEqualTo(5);
      assertThat(deserialized.playlistId()).isEqualTo(playlistId);
      assertThat(deserialized.filter()).isNull();
    }

    @Test
    @DisplayName("SettingsCommand serialization round-trip with filter")
    void testSettingsCommandWithFilter() throws Exception {
      RoomFilterPayload filter =
          new RoomFilterPayload(List.of("POP", "BALLAD"), null, List.of(2020), null);
      SettingsCommand cmd = new SettingsCommand(10, 5, null, filter);
      String json = jsonMapper.writeValueAsString(cmd);
      SettingsCommand deserialized = jsonMapper.readValue(json, SettingsCommand.class);
      assertThat(deserialized.roundCount()).isEqualTo(10);
      assertThat(deserialized.maxPlayers()).isEqualTo(5);
      assertThat(deserialized.playlistId()).isNull();
      assertThat(deserialized.filter()).isNotNull();
      assertThat(deserialized.filter().genreCodes()).containsExactly("POP", "BALLAD");
      assertThat(deserialized.filter().decades()).containsExactly(2020);
    }

    @Test
    @DisplayName("KickCommand serialization round-trip")
    void testKickCommand() throws Exception {
      UUID target = UUID.randomUUID();
      KickCommand cmd = new KickCommand(target);
      String json = jsonMapper.writeValueAsString(cmd);
      KickCommand deserialized = jsonMapper.readValue(json, KickCommand.class);
      assertThat(deserialized.targetUserId()).isEqualTo(target);
    }

    @Test
    @DisplayName("RtcCommand serialization round-trip")
    void testRtcCommand() throws Exception {
      UUID target = UUID.randomUUID();
      RtcCommand cmd = new RtcCommand(target, "offer", "{\"sdp\":\"...\"}");
      String json = jsonMapper.writeValueAsString(cmd);
      RtcCommand deserialized = jsonMapper.readValue(json, RtcCommand.class);
      assertThat(deserialized.targetUserId()).isEqualTo(target);
      assertThat(deserialized.signalType()).isEqualTo("offer");
      assertThat(deserialized.payload()).isEqualTo("{\"sdp\":\"...\"}");
    }

    @Test
    @DisplayName("Commands with roundNo round-trip")
    void testRoundCommands() throws Exception {
      PreparedCommand prep = new PreparedCommand(2);
      assertThat(
              jsonMapper
                  .readValue(jsonMapper.writeValueAsString(prep), PreparedCommand.class)
                  .roundNo())
          .isEqualTo(2);

      ListenMoreCommand lm = new ListenMoreCommand(2, 5);
      ListenMoreCommand lmDeser =
          jsonMapper.readValue(jsonMapper.writeValueAsString(lm), ListenMoreCommand.class);
      assertThat(lmDeser.roundNo()).isEqualTo(2);
      assertThat(lmDeser.currentLevel()).isEqualTo(5);

      GiveUpCommand gu = new GiveUpCommand(3);
      assertThat(
              jsonMapper
                  .readValue(jsonMapper.writeValueAsString(gu), GiveUpCommand.class)
                  .roundNo())
          .isEqualTo(3);

      UUID trackId = UUID.randomUUID();
      GuessCommand gc = new GuessCommand(3, trackId);
      GuessCommand gcDeser =
          jsonMapper.readValue(jsonMapper.writeValueAsString(gc), GuessCommand.class);
      assertThat(gcDeser.roundNo()).isEqualTo(3);
      assertThat(gcDeser.trackId()).isEqualTo(trackId);

      ChatCommand cc = new ChatCommand("Hay quá!");
      assertThat(
              jsonMapper.readValue(jsonMapper.writeValueAsString(cc), ChatCommand.class).content())
          .isEqualTo("Hay quá!");
    }
  }

  @Nested
  @DisplayName("Lobby Events Serialization & AC Tests")
  class LobbyEventsTests {

    @Test
    @DisplayName("PlayerJoinedEvent includes user with isConnected and serverTime")
    void testPlayerJoinedEvent() throws Exception {
      Instant now = Instant.parse("2026-10-29T10:00:00Z");
      RoomPlayerSummaryPayload player =
          new RoomPlayerSummaryPayload(
              UUID.randomUUID(), "PlayerOne", null, true, true, false, true, 0);
      PlayerJoinedEvent event = new PlayerJoinedEvent(player, now);

      String json = jsonMapper.writeValueAsString(event);
      PlayerJoinedEvent deserialized = jsonMapper.readValue(json, PlayerJoinedEvent.class);

      assertThat(deserialized.type()).isEqualTo("PLAYER_JOINED");
      assertThat(deserialized.user().nickname()).isEqualTo("PlayerOne");
      assertThat(deserialized.user().isConnected()).isTrue();
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("RoomSettingsChangedEvent includes filter when configured")
    void testRoomSettingsChangedEventWithFilter() throws Exception {
      Instant now = Instant.parse("2026-10-29T10:00:18Z");
      RoomFilterPayload filter =
          new RoomFilterPayload(List.of("POP", "BALLAD"), null, List.of(2020), null);
      RoomSettingsChangedEvent event = new RoomSettingsChangedEvent(15, 5, null, filter, now);
      String json = jsonMapper.writeValueAsString(event);
      RoomSettingsChangedEvent deserialized =
          jsonMapper.readValue(json, RoomSettingsChangedEvent.class);
      assertThat(deserialized.type()).isEqualTo("ROOM_SETTINGS_CHANGED");
      assertThat(deserialized.roundCount()).isEqualTo(15);
      assertThat(deserialized.maxPlayers()).isEqualTo(5);
      assertThat(deserialized.playlistId()).isNull();
      assertThat(deserialized.filter()).isNotNull();
      assertThat(deserialized.filter().genreCodes()).containsExactly("POP", "BALLAD");
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("PlayerConnectionChangedEvent (BR-14) broadcasts disconnection status")
    void testPlayerConnectionChangedEvent() throws Exception {
      Instant now = Instant.now();
      UUID userId = UUID.randomUUID();
      PlayerConnectionChangedEvent event = new PlayerConnectionChangedEvent(userId, false, now);

      String json = jsonMapper.writeValueAsString(event);
      PlayerConnectionChangedEvent deserialized =
          jsonMapper.readValue(json, PlayerConnectionChangedEvent.class);

      assertThat(deserialized.type()).isEqualTo("PLAYER_CONNECTION_CHANGED");
      assertThat(deserialized.userId()).isEqualTo(userId);
      assertThat(deserialized.isConnected()).isFalse();
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("MatchCountdownEvent (BR-13) 30s countdown")
    void testMatchCountdown() throws Exception {
      Instant now = Instant.now();
      MatchCountdownEvent event = new MatchCountdownEvent(30, now);
      String json = jsonMapper.writeValueAsString(event);
      MatchCountdownEvent deserialized = jsonMapper.readValue(json, MatchCountdownEvent.class);

      assertThat(deserialized.type()).isEqualTo("MATCH_COUNTDOWN");
      assertThat(deserialized.remainingSeconds()).isEqualTo(30);
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("MatchSoloSuggestionEvent (BR-13) after 60s waiting alone")
    void testSoloSuggestion() throws Exception {
      Instant now = Instant.now();
      MatchSoloSuggestionEvent event = new MatchSoloSuggestionEvent("Chơi đơn?", now);
      String json = jsonMapper.writeValueAsString(event);
      MatchSoloSuggestionEvent deserialized =
          jsonMapper.readValue(json, MatchSoloSuggestionEvent.class);

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
                      UUID.randomUUID(), "LateUser", null, false, false, true, true, 0)),
              now);
      WaitingListChangedEvent wlDeser =
          jsonMapper.readValue(
              jsonMapper.writeValueAsString(wlEvent), WaitingListChangedEvent.class);
      assertThat(wlDeser.waitingPlayers()).hasSize(1);
      assertThat(wlDeser.waitingPlayers().getFirst().isConnected()).isTrue();

      ReturnToLobbyEvent rtl = new ReturnToLobbyEvent(now);
      ReturnToLobbyEvent rtlDeser =
          jsonMapper.readValue(jsonMapper.writeValueAsString(rtl), ReturnToLobbyEvent.class);
      assertThat(rtlDeser.type()).isEqualTo("RETURN_TO_LOBBY");
      assertThat(rtlDeser.serverTime()).isEqualTo(now);
    }
  }

  @Nested
  @DisplayName("Fairness & Anti-Leak Tests (AC 3, AC 4, B1, M1)")
  class FairnessAndAntiCheatTests {

    @Test
    @DisplayName(
        "Active round events NEVER leak track metadata or broadcast audioClip (AC 4, NFR-07, B1, M1)")
    void activeRoundEventsDoNotLeakAnswersOrBroadcastAudioClip() {
      Class<?>[] broadcastEventClasses = {
        RoundPrepareEvent.class,
        RoundStartEvent.class,
        ListenMoreOpenedEvent.class,
        PlayerGaveUpEvent.class,
        PlayerGuessedEvent.class,
        RoundTrackReplacedEvent.class
      };

      for (Class<?> clazz : broadcastEventClasses) {
        for (Field field : clazz.getDeclaredFields()) {
          String fieldName = field.getName().toLowerCase();
          assertThat(fieldName)
              .withFailMessage(
                  "Broadcast class %s leaks answer metadata through field '%s'",
                  clazz.getSimpleName(), field.getName())
              .doesNotContain("tracktitle")
              .doesNotContain("artistname")
              .doesNotContain("audioclip")
              .doesNotContain("trackid")
              .doesNotContain("previewfullurl");
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

      String json = jsonMapper.writeValueAsString(event);
      assertThat(json).doesNotContain("trackId");
      assertThat(json).doesNotContain("trackTitle");
      assertThat(json).doesNotContain("artistName");

      PlayerGuessedEvent deserialized = jsonMapper.readValue(json, PlayerGuessedEvent.class);
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
      String json = jsonMapper.writeValueAsString(event);

      RoundEndEvent deserialized = jsonMapper.readValue(json, RoundEndEvent.class);
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
      MatchEarlyEndedEvent event =
          new MatchEarlyEndedEvent("ONLY_ONE_PLAYER_REMAINED", survivor, now);

      String json = jsonMapper.writeValueAsString(event);
      MatchEarlyEndedEvent deserialized = jsonMapper.readValue(json, MatchEarlyEndedEvent.class);

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
      String json = jsonMapper.writeValueAsString(event);
      MatchFinishedEvent deserialized = jsonMapper.readValue(json, MatchFinishedEvent.class);

      assertThat(deserialized.type()).isEqualTo("MATCH_FINISHED");
      assertThat(deserialized.rankings()).hasSize(2);
      assertThat(deserialized.rankings().get(0).totalCorrectTimeMs()).isEqualTo(42000L);
      assertThat(deserialized.rankings().get(1).totalCorrectTimeMs()).isEqualTo(56000L);
    }
  }

  @Nested
  @DisplayName("Private Queue & Audio Clip Isolation Tests (B1, M1, M2, BR-04, BR-14)")
  class PrivateQueueTests {

    @Test
    @DisplayName("RoundAudioClipMessage delivers private audio token to player")
    void roundAudioClipMessage() throws Exception {
      Instant now = Instant.now();
      AudioClipPayload clip =
          new AudioClipPayload("/api/v1/audio/clips/secret-token", 2, now.plusSeconds(300));
      RoundAudioClipMessage msg = new RoundAudioClipMessage(1, 2, clip, now);

      String json = jsonMapper.writeValueAsString(msg);
      RoundAudioClipMessage deserialized = jsonMapper.readValue(json, RoundAudioClipMessage.class);

      assertThat(deserialized.type()).isEqualTo("ROUND_AUDIO_CLIP");
      assertThat(deserialized.roundNo()).isEqualTo(1);
      assertThat(deserialized.level()).isEqualTo(2);
      assertThat(deserialized.audioClip().url()).isEqualTo("/api/v1/audio/clips/secret-token");
    }

    @Test
    @DisplayName("GuessResultMessage wrong guess unlocks nextLevel and nextAudioClip (BR-04, M2)")
    void guessResultWrongUnlocksNextLevel() throws Exception {
      Instant now = Instant.now();
      AudioClipPayload nextClip =
          new AudioClipPayload("/api/v1/audio/clips/level5-token", 5, now.plusSeconds(300));
      GuessResultMessage msg =
          new GuessResultMessage(1, "WRONG_ARTIST", false, true, null, 5, nextClip, now);

      String json = jsonMapper.writeValueAsString(msg);
      GuessResultMessage deserialized = jsonMapper.readValue(json, GuessResultMessage.class);

      assertThat(deserialized.type()).isEqualTo("GUESS_RESULT_PRIVATE");
      assertThat(deserialized.isCorrect()).isFalse();
      assertThat(deserialized.isCorrectArtist()).isTrue();
      assertThat(deserialized.nextLevel()).isEqualTo(5);
      assertThat(deserialized.nextAudioClip()).isNotNull();
      assertThat(deserialized.nextAudioClip().level()).isEqualTo(5);
    }

    @Test
    @DisplayName("ListenMoreResponseMessage provides private clip token")
    void listenMoreResponse() throws Exception {
      Instant now = Instant.now();
      AudioClipPayload clip =
          new AudioClipPayload("/api/v1/audio/clips/token5", 5, now.plusSeconds(300));
      ListenMoreResponseMessage msg = new ListenMoreResponseMessage(1, 5, clip, now);

      String json = jsonMapper.writeValueAsString(msg);
      ListenMoreResponseMessage deserialized =
          jsonMapper.readValue(json, ListenMoreResponseMessage.class);

      assertThat(deserialized.type()).isEqualTo("LISTEN_MORE_RESPONSE");
      assertThat(deserialized.level()).isEqualTo(5);
      assertThat(deserialized.audioClip().url()).isEqualTo("/api/v1/audio/clips/token5");
    }

    @Test
    @DisplayName("ErrorMessage supports blocked chat notification (BR-16, BR-17)")
    void errorMessageBlockedChat() throws Exception {
      Instant now = Instant.now();
      ErrorMessage err =
          new ErrorMessage(
              "CHAT_ANSWER_LEAK",
              "Tin nhắn bị chặn do vi phạm quy tắc phòng (BR-16)",
              "Chứa tên bài hát trong vòng chơi",
              now);

      String json = jsonMapper.writeValueAsString(err);
      ErrorMessage deserialized = jsonMapper.readValue(json, ErrorMessage.class);

      assertThat(deserialized.type()).isEqualTo("ERROR");
      assertThat(deserialized.code()).isEqualTo("CHAT_ANSWER_LEAK");
      assertThat(deserialized.serverTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("ErrorMessage supports start failure with missing tracks (UC-17, BR-09, M6)")
    void errorMessageStartFailure() throws Exception {
      Instant now = Instant.now();
      ErrorMessage err =
          new ErrorMessage(
              "NOT_ENOUGH_TRACKS",
              "Playlist không đủ bài hát để bắt đầu trận.",
              "Cần ít nhất 10 bài hát nhưng danh sách phát chỉ có 7 bài.",
              "NOT_ENOUGH_TRACKS",
              3,
              now);

      String json = jsonMapper.writeValueAsString(err);
      ErrorMessage deserialized = jsonMapper.readValue(json, ErrorMessage.class);

      assertThat(deserialized.type()).isEqualTo("ERROR");
      assertThat(deserialized.code()).isEqualTo("NOT_ENOUGH_TRACKS");
      assertThat(deserialized.startFailureReason()).isEqualTo("NOT_ENOUGH_TRACKS");
      assertThat(deserialized.missingTracks()).isEqualTo(3);
    }

    @Test
    @DisplayName(
        "ResyncStateMessage (BR-14) delivers full room snapshot with revealed answer in ROUND_REVEAL")
    void resyncStateMessageSnapshot() throws Exception {
      Instant now = Instant.now();
      UUID roomId = UUID.randomUUID();
      UUID hostId = UUID.randomUUID();

      RoomPlayerSummaryPayload host =
          new RoomPlayerSummaryPayload(hostId, "HostUser", null, true, true, false, true, 1000);

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
              new AudioClipPayload("/api/v1/audio/token", 2, now.plusSeconds(300)),
              35000,
              false);

      RoundAnswerPayload lastAnswer =
          new RoundAnswerPayload(
              UUID.randomUUID(),
              "Bài Hát Vừa Xong",
              "Ca Sĩ",
              null,
              null,
              "https://deezer.com/track/1");
      PlayerRoundResultPayload lastResult =
          new PlayerRoundResultPayload(hostId, "CORRECT", 2, 1000, 0, 180, 1180, 0, 5000L);

      ResyncStateMessage resync =
          new ResyncStateMessage(
              room,
              "ROUND_REVEAL",
              currentRound,
              List.of(new PlayerScoreSummaryPayload(hostId, "HostUser", 1000, 1, 1)),
              lastAnswer,
              List.of(lastResult),
              now);

      String json = jsonMapper.writeValueAsString(resync);
      ResyncStateMessage deserialized = jsonMapper.readValue(json, ResyncStateMessage.class);

      assertThat(deserialized.type()).isEqualTo("RESYNC_STATE");
      assertThat(deserialized.room().code()).isEqualTo("MEL889");
      assertThat(deserialized.roomPhase()).isEqualTo("ROUND_REVEAL");
      assertThat(deserialized.lastRoundAnswer()).isNotNull();
      assertThat(deserialized.lastRoundAnswer().trackTitle()).isEqualTo("Bài Hát Vừa Xong");
      assertThat(deserialized.lastRoundResults()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Contract Examples Deserialization Tests (M3, M4)")
  class ExamplesDeserializationTests {

    private Path examplesDir() {
      Path path = Path.of("../contracts/examples/room");
      if (!Files.exists(path)) {
        path = Path.of("contracts/examples/room");
      }
      return path;
    }

    private String readJson(String fileName) throws Exception {
      Path file = examplesDir().resolve(fileName);
      assertThat(file).exists();
      return Files.readString(file);
    }

    @Test
    @DisplayName("Verify and deserialize all command examples")
    void testCommandExamples() throws Exception {
      ReadyCommand ready = jsonMapper.readValue(readJson("command.ready.json"), ReadyCommand.class);
      assertThat(ready.ready()).isTrue();

      SettingsCommand settings =
          jsonMapper.readValue(readJson("command.settings.json"), SettingsCommand.class);
      assertThat(settings.roundCount()).isEqualTo(10);
      assertThat(settings.maxPlayers()).isEqualTo(5);

      SettingsCommand settingsFilter =
          jsonMapper.readValue(readJson("command.settings.filter.json"), SettingsCommand.class);
      assertThat(settingsFilter.roundCount()).isEqualTo(10);
      assertThat(settingsFilter.filter()).isNotNull();
      assertThat(settingsFilter.filter().genreCodes()).containsExactly("POP", "BALLAD");

      KickCommand kick = jsonMapper.readValue(readJson("command.kick.json"), KickCommand.class);
      assertThat(kick.targetUserId()).isNotNull();

      PreparedCommand prep =
          jsonMapper.readValue(readJson("command.prepared.json"), PreparedCommand.class);
      assertThat(prep.roundNo()).isEqualTo(1);

      ListenMoreCommand lm =
          jsonMapper.readValue(readJson("command.listen-more.json"), ListenMoreCommand.class);
      assertThat(lm.roundNo()).isEqualTo(1);
      assertThat(lm.currentLevel()).isEqualTo(2);

      GiveUpCommand gu =
          jsonMapper.readValue(readJson("command.give-up.json"), GiveUpCommand.class);
      assertThat(gu.roundNo()).isEqualTo(1);

      GuessCommand guess = jsonMapper.readValue(readJson("command.guess.json"), GuessCommand.class);
      assertThat(guess.roundNo()).isEqualTo(1);
      assertThat(guess.trackId()).isNotNull();

      ChatCommand chat = jsonMapper.readValue(readJson("command.chat.json"), ChatCommand.class);
      assertThat(chat.content()).isEqualTo("Bài này hay quá!");

      RtcCommand rtc = jsonMapper.readValue(readJson("command.rtc.json"), RtcCommand.class);
      assertThat(rtc.signalType()).isEqualTo("offer");
    }

    @Test
    @DisplayName("Verify and deserialize all lobby event examples")
    void testLobbyEventExamples() throws Exception {
      PlayerJoinedEvent pj =
          jsonMapper.readValue(readJson("event.lobby.player-joined.json"), PlayerJoinedEvent.class);
      assertThat(pj.type()).isEqualTo("PLAYER_JOINED");
      assertThat(pj.user().isConnected()).isTrue();

      PlayerLeftEvent pl =
          jsonMapper.readValue(readJson("event.lobby.player-left.json"), PlayerLeftEvent.class);
      assertThat(pl.type()).isEqualTo("PLAYER_LEFT");
      assertThat(pl.reason()).isEqualTo("DISCONNECTED");

      PlayerConnectionChangedEvent pcc =
          jsonMapper.readValue(
              readJson("event.lobby.player-connection-changed.json"),
              PlayerConnectionChangedEvent.class);
      assertThat(pcc.type()).isEqualTo("PLAYER_CONNECTION_CHANGED");
      assertThat(pcc.isConnected()).isFalse();

      PlayerReadyChangedEvent prc =
          jsonMapper.readValue(
              readJson("event.lobby.player-ready-changed.json"), PlayerReadyChangedEvent.class);
      assertThat(prc.type()).isEqualTo("PLAYER_READY_CHANGED");
      assertThat(prc.isReady()).isTrue();

      PlayerKickedEvent pk =
          jsonMapper.readValue(readJson("event.lobby.player-kicked.json"), PlayerKickedEvent.class);
      assertThat(pk.type()).isEqualTo("PLAYER_KICKED");

      RoomSettingsChangedEvent rsc =
          jsonMapper.readValue(
              readJson("event.lobby.settings-changed.json"), RoomSettingsChangedEvent.class);
      assertThat(rsc.type()).isEqualTo("ROOM_SETTINGS_CHANGED");

      RoomSettingsChangedEvent rscFilter =
          jsonMapper.readValue(
              readJson("event.lobby.settings-changed.filter.json"), RoomSettingsChangedEvent.class);
      assertThat(rscFilter.type()).isEqualTo("ROOM_SETTINGS_CHANGED");
      assertThat(rscFilter.filter()).isNotNull();
      assertThat(rscFilter.filter().decades()).containsExactly(2020);

      MatchStartingEvent ms =
          jsonMapper.readValue(
              readJson("event.lobby.match-starting.json"), MatchStartingEvent.class);
      assertThat(ms.type()).isEqualTo("MATCH_STARTING");

      MatchCountdownEvent mc =
          jsonMapper.readValue(
              readJson("event.lobby.match-countdown.json"), MatchCountdownEvent.class);
      assertThat(mc.type()).isEqualTo("MATCH_COUNTDOWN");

      MatchCountdownStoppedEvent mcs =
          jsonMapper.readValue(
              readJson("event.lobby.match-countdown-stopped.json"),
              MatchCountdownStoppedEvent.class);
      assertThat(mcs.type()).isEqualTo("MATCH_COUNTDOWN_STOPPED");
      assertThat(mcs.reason()).isEqualTo("PLAYER_LEFT");

      MatchSoloSuggestionEvent mss =
          jsonMapper.readValue(
              readJson("event.lobby.match-solo-suggestion.json"), MatchSoloSuggestionEvent.class);
      assertThat(mss.type()).isEqualTo("MATCH_SOLO_SUGGESTION");

      WaitingListChangedEvent wlc =
          jsonMapper.readValue(
              readJson("event.lobby.waiting-list-changed.json"), WaitingListChangedEvent.class);
      assertThat(wlc.type()).isEqualTo("WAITING_LIST_CHANGED");
      assertThat(wlc.waitingPlayers().getFirst().isConnected()).isTrue();

      ReturnToLobbyEvent rtl =
          jsonMapper.readValue(
              readJson("event.lobby.return-to-lobby.json"), ReturnToLobbyEvent.class);
      assertThat(rtl.type()).isEqualTo("RETURN_TO_LOBBY");
    }

    @Test
    @DisplayName("Verify and deserialize all round and gameplay event examples")
    void testRoundEventExamples() throws Exception {
      RoundPrepareEvent rpe =
          jsonMapper.readValue(readJson("event.round.prepare.json"), RoundPrepareEvent.class);
      assertThat(rpe.type()).isEqualTo("ROUND_PREPARE");
      assertThat(rpe.roundNo()).isEqualTo(1);

      RoundStartEvent rse =
          jsonMapper.readValue(readJson("event.round.start.json"), RoundStartEvent.class);
      assertThat(rse.type()).isEqualTo("ROUND_START");
      assertThat(rse.roundNo()).isEqualTo(1);

      ListenMoreOpenedEvent lmo =
          jsonMapper.readValue(
              readJson("event.round.listen-more-opened.json"), ListenMoreOpenedEvent.class);
      assertThat(lmo.type()).isEqualTo("LISTEN_MORE_OPENED");
      assertThat(lmo.level()).isEqualTo(5);

      PlayerGaveUpEvent pgu =
          jsonMapper.readValue(
              readJson("event.round.player-gave-up.json"), PlayerGaveUpEvent.class);
      assertThat(pgu.type()).isEqualTo("PLAYER_GAVE_UP");

      PlayerGuessedEvent pge =
          jsonMapper.readValue(
              readJson("event.round.player-guessed.json"), PlayerGuessedEvent.class);
      assertThat(pge.type()).isEqualTo("PLAYER_GUESSED");
      assertThat(pge.isCorrect()).isTrue();

      RoundTrackReplacedEvent rtr =
          jsonMapper.readValue(
              readJson("event.round.track-replaced.json"), RoundTrackReplacedEvent.class);
      assertThat(rtr.type()).isEqualTo("ROUND_TRACK_REPLACED");

      RoundEndEvent ree =
          jsonMapper.readValue(readJson("event.round.end.json"), RoundEndEvent.class);
      assertThat(ree.type()).isEqualTo("ROUND_END");
      assertThat(ree.answer().trackTitle()).isEqualTo("Cắt Đôi Nỗi Sầu");

      MatchEarlyEndedEvent mee =
          jsonMapper.readValue(
              readJson("event.round.early-ended.json"), MatchEarlyEndedEvent.class);
      assertThat(mee.type()).isEqualTo("MATCH_EARLY_ENDED");
      assertThat(mee.reason()).isEqualTo("ONLY_ONE_PLAYER_REMAINED");
    }

    @Test
    @DisplayName("Verify and deserialize all scoreboard, chat, rtc and queue examples")
    void testScoreboardAndQueueExamples() throws Exception {
      ScoreboardUpdateEvent sue =
          jsonMapper.readValue(
              readJson("event.scoreboard.update.json"), ScoreboardUpdateEvent.class);
      assertThat(sue.type()).isEqualTo("SCOREBOARD_UPDATE");

      MatchFinishedEvent mfe =
          jsonMapper.readValue(
              readJson("event.scoreboard.match-finished.json"), MatchFinishedEvent.class);
      assertThat(mfe.type()).isEqualTo("MATCH_FINISHED");

      ChatMessageEvent cme =
          jsonMapper.readValue(readJson("event.chat.message.json"), ChatMessageEvent.class);
      assertThat(cme.type()).isEqualTo("CHAT_MESSAGE");

      RtcSignalEvent rse =
          jsonMapper.readValue(readJson("event.rtc.signal.json"), RtcSignalEvent.class);
      assertThat(rse.type()).isEqualTo("RTC_SIGNAL");

      ErrorMessage em = jsonMapper.readValue(readJson("queue.error.json"), ErrorMessage.class);
      assertThat(em.type()).isEqualTo("ERROR");
      assertThat(em.code()).isEqualTo("CHAT_ANSWER_LEAK");

      ResyncStateMessage rsm =
          jsonMapper.readValue(readJson("queue.resync-state.json"), ResyncStateMessage.class);
      assertThat(rsm.type()).isEqualTo("RESYNC_STATE");
      assertThat(rsm.room().players().getFirst().isConnected()).isTrue();

      ListenMoreResponseMessage lmrm =
          jsonMapper.readValue(
              readJson("queue.listen-more-response.json"), ListenMoreResponseMessage.class);
      assertThat(lmrm.type()).isEqualTo("LISTEN_MORE_RESPONSE");
      assertThat(lmrm.audioClip().level()).isEqualTo(5);

      GuessResultMessage grmCorrect =
          jsonMapper.readValue(readJson("queue.guess-result.json"), GuessResultMessage.class);
      assertThat(grmCorrect.type()).isEqualTo("GUESS_RESULT_PRIVATE");
      assertThat(grmCorrect.isCorrect()).isTrue();

      GuessResultMessage grmWrong =
          jsonMapper.readValue(readJson("queue.guess-result.wrong.json"), GuessResultMessage.class);
      assertThat(grmWrong.type()).isEqualTo("GUESS_RESULT_PRIVATE");
      assertThat(grmWrong.isCorrect()).isFalse();
      assertThat(grmWrong.nextLevel()).isEqualTo(5);
      assertThat(grmWrong.nextAudioClip()).isNotNull();

      RoundAudioClipMessage racm =
          jsonMapper.readValue(
              readJson("queue.round-audio-clip.json"), RoundAudioClipMessage.class);
      assertThat(racm.type()).isEqualTo("ROUND_AUDIO_CLIP");
      assertThat(racm.audioClip().level()).isEqualTo(2);
    }
  }
}
