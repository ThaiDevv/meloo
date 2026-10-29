package com.meloo.game.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meloo.IntegrationTest;
import com.meloo.audio.api.AccountLockChecker;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.events.MatchEndStatus;
import com.meloo.game.events.MatchFinished;
import com.meloo.game.events.MatchStarted;
import com.meloo.game.solo.SoloGameService;
import com.meloo.game.solo.SoloMatchManager;
import com.meloo.generated.model.CreateSoloMatchRequestDto;
import com.meloo.generated.model.GuessRequestDto;
import com.meloo.generated.model.SoloFilterDto;
import com.meloo.generated.model.SoloModeDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Import(SoloGameIntegrationTest.TestConfig.class)
class SoloGameIntegrationTest extends IntegrationTest {

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "MELOO_JWT_SECRET", () -> "test-meloo-jwt-secret-key-with-minimum-32-bytes-length-xyz");
    registry.add("meloo.audio.provider.type", () -> "fake");
    registry.add(
        "meloo.audio.hmac-secret",
        () -> "test-hmac-secret-key-for-unit-and-integration-tests-must-be-long-enough");
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    MutableTestClock mutableTestClock() {
      return new MutableTestClock(Instant.parse("2026-10-29T12:00:00Z"));
    }

    @Bean
    TestMatchFinishedListener testMatchFinishedListener() {
      return new TestMatchFinishedListener();
    }
  }

  public static class MutableTestClock extends Clock {
    private Instant now;

    public MutableTestClock(Instant initial) {
      this.now = initial;
    }

    public void setNow(Instant now) {
      this.now = now;
    }

    public void advance(Duration duration) {
      this.now = this.now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  public static class TestMatchFinishedListener {
    final List<MatchFinished> finishedEvents = new CopyOnWriteArrayList<>();
    final List<MatchStarted> startedEvents = new CopyOnWriteArrayList<>();

    @ApplicationModuleListener
    void onMatchFinished(MatchFinished event) {
      finishedEvents.add(event);
    }

    @ApplicationModuleListener
    void onMatchStarted(MatchStarted event) {
      startedEvents.add(event);
    }

    public List<MatchFinished> getFinishedEvents() {
      return finishedEvents;
    }

    public List<MatchStarted> getStartedEvents() {
      return startedEvents;
    }

    public void clear() {
      finishedEvents.clear();
      startedEvents.clear();
    }
  }

  @Autowired private MockMvc mockMvc;
  private final tools.jackson.databind.ObjectMapper objectMapper =
      new tools.jackson.databind.ObjectMapper();
  @Autowired private MutableTestClock testClock;
  @Autowired private TestMatchFinishedListener testListener;
  @Autowired private SoloMatchManager matchManager;
  @Autowired private SoloGameService soloGameService;

  @MockitoBean private CatalogService catalogService;
  @MockitoBean private AudioService audioService;
  @MockitoBean private AccountLockChecker accountLockChecker;

  private final Map<UUID, TrackInfo> tracksById = new ConcurrentHashMap<>();
  private final List<TrackInfo> standardTracks = new ArrayList<>();

  @BeforeEach
  void setUp() {
    testClock.setNow(Instant.parse("2026-10-29T12:00:00Z"));
    testListener.clear();
    tracksById.clear();
    standardTracks.clear();

    when(accountLockChecker.isAccountLocked(any())).thenReturn(false);

    // Chuẩn bị 10 bài hát mẫu trong kho nhạc
    for (int i = 1; i <= 10; i++) {
      UUID id = UUID.randomUUID();
      TrackInfo track =
          new TrackInfo(
              id,
              "Bài Hát " + i,
              "Ca Sĩ " + i,
              "DEEZER",
              "100" + i,
              "https://cover.test/" + i + ".jpg",
              null,
              "VN",
              (short) 2024);
      tracksById.put(id, track);
      standardTracks.add(track);

      when(catalogService.getTrackById(id)).thenReturn(Optional.of(track));
    }

    when(catalogService.selectRandomTracksByPlaylistCode(eq("standard"), anyInt(), any()))
        .thenAnswer(
            invocation -> {
              int count = invocation.getArgument(1);
              Set<UUID> excluded = invocation.getArgument(2);
              List<TrackInfo> result = new ArrayList<>();
              for (TrackInfo t : standardTracks) {
                if (!excluded.contains(t.id())) {
                  result.add(t);
                  if (result.size() >= count) break;
                }
              }
              return result;
            });

    when(catalogService.selectRandomTracks(any(), anyInt(), any()))
        .thenAnswer(
            invocation -> {
              int count = invocation.getArgument(1);
              Set<UUID> excluded = invocation.getArgument(2);
              List<TrackInfo> result = new ArrayList<>();
              for (TrackInfo t : standardTracks) {
                if (!excluded.contains(t.id())) {
                  result.add(t);
                  if (result.size() >= count) break;
                }
              }
              return result;
            });

    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());

    when(audioService.createAudioClip(any(), any(), anyInt()))
        .thenAnswer(
            invocation -> {
              int level = invocation.getArgument(2);
              var clip = new com.meloo.generated.model.AudioClipDto();
              clip.setUrl("/api/v1/audio/mock-token-level-" + level);
              clip.setExpiresAt(
                  testClock.instant().plus(Duration.ofMinutes(2)).atOffset(ZoneOffset.UTC));
              return clip;
            });
  }

  @Test
  @DisplayName(
      "Integration 5 vòng đầy đủ các nhánh: đúng 2s, nghe thêm rồi đúng, bỏ qua 30s, sai hết lượt, thay bài do lỗi audio, combo")
  void testFiveRoundCompleteGameFlowWithComboAndSkip() throws Exception {
    UUID userId = UUID.randomUUID();

    // 1. Tạo trận 5 vòng PRACTICE
    CreateSoloMatchRequestDto createReq = new CreateSoloMatchRequestDto();
    createReq.setMode(SoloModeDto.PRACTICE);
    createReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult createRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roundCount").value(5))
            .andExpect(jsonPath("$.totalScore").value(0))
            .andExpect(jsonPath("$.comboStreak").value(0))
            .andExpect(jsonPath("$.currentRound.roundNo").value(1))
            .andExpect(jsonPath("$.currentRound.level").value(1))
            .andReturn();

    String matchIdStr = JsonPath.read(createRes.getResponse().getContentAsString(), "$.matchId");
    UUID matchId = UUID.fromString(matchIdStr);

    // ==========================================
    // VÒNG 1: Đúng ở 2s (level 1) -> +1000 điểm, combo streak = 1 (combo 0%)
    // ==========================================
    TrackInfo track1 = standardTracks.get(0);
    GuessRequestDto guess1 = new GuessRequestDto().trackId(track1.id());

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guess1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.roundEnded").value(true))
        .andExpect(jsonPath("$.end.result.roundScore").value(1000))
        .andExpect(jsonPath("$.end.result.totalScore").value(1000))
        .andExpect(jsonPath("$.end.result.comboStreak").value(1))
        .andExpect(jsonPath("$.end.result.comboPercent").value(0))
        .andExpect(jsonPath("$.end.matchFinished").value(false))
        .andExpect(jsonPath("$.end.nextRound.roundNo").value(2));

    // ==========================================
    // VÒNG 2: Nghe thêm lên level 2 (5s) rồi đoán đúng -> +800 điểm cơ bản + 10% combo = 880 điểm
    // Tổng điểm: 1000 + 880 = 1880, combo streak = 2
    // ==========================================
    TrackInfo track2 = standardTracks.get(1);

    // Nghe thêm lên level 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/listen-more", matchId)
                .param("expectedLevel", "2")
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value(2))
        .andExpect(jsonPath("$.listenSeconds").value(5));

    GuessRequestDto guess2 = new GuessRequestDto().trackId(track2.id());
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guess2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.end.result.baseScore").value(800))
        .andExpect(jsonPath("$.end.result.comboPercent").value(10))
        .andExpect(jsonPath("$.end.result.roundScore").value(880))
        .andExpect(jsonPath("$.end.result.totalScore").value(1880))
        .andExpect(jsonPath("$.end.result.comboStreak").value(2))
        .andExpect(jsonPath("$.end.nextRound.roundNo").value(3));

    // ==========================================
    // VÒNG 3: Nghe thêm đến level 5 (30s) rồi bỏ qua (skip) -> 0 điểm, đứt combo (streak = 0)
    // ==========================================
    // Nghe thêm 1 -> 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/listen-more", matchId)
                .param("expectedLevel", "2")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());
    // Nghe thêm 2 -> 3
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/listen-more", matchId)
                .param("expectedLevel", "3")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());
    // Nghe thêm 3 -> 4
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/listen-more", matchId)
                .param("expectedLevel", "4")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());
    // Nghe thêm 4 -> 5
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/listen-more", matchId)
                .param("expectedLevel", "5")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk());

    // Skip ở level 5
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/skip", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.outcome").value("GAVE_UP"))
        .andExpect(jsonPath("$.result.roundScore").value(0))
        .andExpect(jsonPath("$.result.comboStreak").value(0))
        .andExpect(jsonPath("$.result.totalScore").value(1880))
        .andExpect(jsonPath("$.nextRound.roundNo").value(4));

    // ==========================================
    // VÒNG 4: Đoán sai 4 lần mở dần đến 30s, lần 5 đoán sai -> WRONG_OUT, 0 điểm
    // ==========================================
    TrackInfo wrongTrack = standardTracks.get(9); // Bài khác đáp án
    GuessRequestDto wrongGuess = new GuessRequestDto().trackId(wrongTrack.id());

    // Guess sai lần 1 (ở level 1) -> mở level 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/4/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongGuess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(false))
        .andExpect(jsonPath("$.roundEnded").value(false))
        .andExpect(jsonPath("$.round.level").value(2));

    // Guess sai lần 2 -> mở level 3
    mockMvc.perform(
        post("/api/v1/solo/matches/{matchId}/rounds/4/guesses", matchId)
            .with(jwt().jwt(j -> j.subject(userId.toString())))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(wrongGuess)));

    // Guess sai lần 3 -> mở level 4
    mockMvc.perform(
        post("/api/v1/solo/matches/{matchId}/rounds/4/guesses", matchId)
            .with(jwt().jwt(j -> j.subject(userId.toString())))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(wrongGuess)));

    // Guess sai lần 4 -> mở level 5 (30s)
    mockMvc.perform(
        post("/api/v1/solo/matches/{matchId}/rounds/4/guesses", matchId)
            .with(jwt().jwt(j -> j.subject(userId.toString())))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(wrongGuess)));

    // Cấu hình trước cho vòng 5: audio của bài 5 bị lỗi ban đầu, bài thay thế phát thành công
    // (BR-27)
    TrackInfo originalTrack5 = standardTracks.get(4);
    TrackInfo replacementTrack5 = standardTracks.get(5);
    when(audioService.prepareRoundAudio(any(), eq(originalTrack5.id()), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("Preview URL 404 from provider"));
    when(audioService.prepareRoundAudio(
            any(), eq(replacementTrack5.id()), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());

    // Guess sai lần 5 ở level 5 -> WRONG_OUT, hết lượt
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/4/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongGuess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(false))
        .andExpect(jsonPath("$.roundEnded").value(true))
        .andExpect(jsonPath("$.end.result.outcome").value("WRONG_OUT"))
        .andExpect(jsonPath("$.end.result.roundScore").value(0))
        .andExpect(jsonPath("$.end.result.totalScore").value(1880))
        .andExpect(jsonPath("$.end.nextRound.roundNo").value(5));

    // ==========================================
    // VÒNG 5: Thay bài do lỗi audio (BR-27), đoán đúng ở level 1 -> +1000 điểm, trận xong
    // ==========================================
    // Đoán đúng bài thay thế
    GuessRequestDto guess5 = new GuessRequestDto().trackId(replacementTrack5.id());
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/5/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guess5)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.roundEnded").value(true))
        .andExpect(jsonPath("$.end.result.roundScore").value(1000))
        .andExpect(jsonPath("$.end.result.totalScore").value(2880))
        .andExpect(jsonPath("$.end.matchFinished").value(true));

    // Kiểm tra kết quả trận đấu qua API getMatchResult
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/result", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINISHED"))
        .andExpect(jsonPath("$.totalScore").value(2880))
        .andExpect(jsonPath("$.correctCount").value(3))
        .andExpect(jsonPath("$.rounds.length()").value(5))
        .andExpect(jsonPath("$.rounds[0].comboStreak").value(1))
        .andExpect(jsonPath("$.rounds[1].comboStreak").value(2))
        .andExpect(jsonPath("$.rounds[2].comboStreak").value(0))
        .andExpect(jsonPath("$.rounds[3].comboStreak").value(0))
        .andExpect(jsonPath("$.rounds[4].comboStreak").value(1));

    // Kiểm tra kết quả riêng từng vòng qua getRoundResult
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/rounds/2/result", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comboStreak").value(2));

    // Kiểm tra sự kiện MatchFinished phát ra qua listener giao dịch
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.getFinishedEvents()).hasSize(1));
    MatchFinished event = testListener.getFinishedEvents().get(0);
    assertThat(event.matchId()).isEqualTo(matchId);
    assertThat(event.mode()).isEqualTo("PRACTICE");
    assertThat(event.ranked()).isFalse();
    assertThat(event.status()).isEqualTo(MatchEndStatus.FINISHED);
    assertThat(event.roundCount()).isEqualTo(5);
    assertThat(event.rounds()).hasSize(5);

    // Vòng 5 ghi nhận đúng bài hát thay thế (provider & providerTrackId)
    assertThat(event.rounds().get(4).trackId()).isEqualTo(replacementTrack5.id());
    assertThat(event.rounds().get(4).provider()).isEqualTo("DEEZER");
    assertThat(event.rounds().get(4).providerTrackId())
        .isEqualTo(replacementTrack5.providerTrackId());
  }

  @Test
  @DisplayName("Combo nối qua 3 vòng liên tiếp: 0% -> 10% -> 20%")
  void testComboThreeConsecutiveRounds() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto createReq = new CreateSoloMatchRequestDto();
    createReq.setMode(SoloModeDto.RANKED);
    createReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult createRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Pro")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID matchId =
        UUID.fromString(JsonPath.read(createRes.getResponse().getContentAsString(), "$.matchId"));

    // Vòng 1: đúng ở level 1 -> 1000 điểm, combo 0%, streak 1
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(jsonPath("$.end.result.roundScore").value(1000))
        .andExpect(jsonPath("$.end.result.comboStreak").value(1))
        .andExpect(jsonPath("$.end.result.comboPercent").value(0))
        .andExpect(jsonPath("$.end.result.totalScore").value(1000));

    // Vòng 2: đúng ở level 1 -> 1000 * 1.1 = 1100 điểm, combo 10%, streak 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(1).id()))))
        .andExpect(jsonPath("$.end.result.roundScore").value(1100))
        .andExpect(jsonPath("$.end.result.comboStreak").value(2))
        .andExpect(jsonPath("$.end.result.comboPercent").value(10))
        .andExpect(jsonPath("$.end.result.totalScore").value(2100));

    // Vòng 3: đúng ở level 1 -> 1000 * 1.2 = 1200 điểm, combo 20%, streak 3
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(2).id()))))
        .andExpect(jsonPath("$.end.result.roundScore").value(1200))
        .andExpect(jsonPath("$.end.result.comboStreak").value(3))
        .andExpect(jsonPath("$.end.result.comboPercent").value(20))
        .andExpect(jsonPath("$.end.result.totalScore").value(3300));
  }

  @Test
  @DisplayName(
      "Phiên mở lại trong 30 phút chơi tiếp bình thường; quá 30 phút phát MatchFinished ABANDONED")
  void testResumeWithin30MinutesAndExpireAfter30Minutes() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto createReq = new CreateSoloMatchRequestDto();
    createReq.setMode(SoloModeDto.PRACTICE);
    createReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult createRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID matchId =
        UUID.fromString(JsonPath.read(createRes.getResponse().getContentAsString(), "$.matchId"));

    // Tiến hành 15 phút sau (dưới 30 phút) -> getActiveSoloMatch thành công
    testClock.advance(Duration.ofMinutes(15));

    mockMvc
        .perform(
            get("/api/v1/solo/matches/active").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(matchId.toString()))
        .andExpect(jsonPath("$.currentRound.roundNo").value(1));

    // Tiến hành tiếp 31 phút sau lần hoạt động gần nhất (tổng quá 30 phút)
    testClock.advance(Duration.ofMinutes(31));

    // Gọi getActiveSoloMatch lúc này sẽ phát hiện hết hạn -> trả 404 NO_ACTIVE_MATCH
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_ACTIVE_MATCH"));

    // Sự kiện MatchFinished được phát ra với trạng thái ABANDONED
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.getFinishedEvents()).hasSize(1));
    MatchFinished event = testListener.getFinishedEvents().get(0);
    assertThat(event.matchId()).isEqualTo(matchId);
    assertThat(event.status()).isEqualTo(MatchEndStatus.ABANDONED);
    assertThat(event.ranked()).isFalse();
  }

  @Test
  @DisplayName("Bỏ trận PRACTICE giữa chừng: rounds không chứa vòng dở; bỏ trận RANKED trả 409")
  void testAbandonPracticeMatchAndRankedMatch() throws Exception {
    UUID userId = UUID.randomUUID();

    // 1. PRACTICE match
    CreateSoloMatchRequestDto practiceReq = new CreateSoloMatchRequestDto();
    practiceReq.setMode(SoloModeDto.PRACTICE);
    practiceReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Tester")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(practiceReq)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID practiceMatchId =
        UUID.fromString(JsonPath.read(res.getResponse().getContentAsString(), "$.matchId"));

    // Hoàn thành vòng 1
    mockMvc.perform(
        post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", practiceMatchId)
            .with(jwt().jwt(j -> j.subject(userId.toString())))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    new GuessRequestDto().trackId(standardTracks.get(0).id()))));

    // Bỏ trận ở vòng 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/abandon", practiceMatchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABANDONED"))
        .andExpect(jsonPath("$.ranked").value(false))
        .andExpect(jsonPath("$.totalScore").value(1000))
        .andExpect(
            jsonPath("$.rounds.length()")
                .value(1)) // Chỉ có vòng 1, vòng 2 dở không nằm trong rounds!
        .andExpect(jsonPath("$.rounds[0].roundNo").value(1));

    // 2. RANKED match
    UUID rankedUserId = UUID.randomUUID();
    CreateSoloMatchRequestDto rankedReq = new CreateSoloMatchRequestDto();
    rankedReq.setMode(SoloModeDto.RANKED);
    rankedReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult rankedRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(
                        jwt()
                            .jwt(
                                j ->
                                    j.subject(rankedUserId.toString())
                                        .claim("nickname", "RankedUser")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(rankedReq)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID rankedMatchId =
        UUID.fromString(JsonPath.read(rankedRes.getResponse().getContentAsString(), "$.matchId"));

    // Bỏ trận RANKED trả 409 RANKED_MATCH_CANNOT_ABANDON
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/abandon", rankedMatchId)
                .with(jwt().jwt(j -> j.subject(rankedUserId.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RANKED_MATCH_CANNOT_ABANDON"));
  }

  @Test
  @DisplayName("Khi đang có trận active, tạo trận mới trả 409 SOLO_MATCH_IN_PROGRESS kèm matchId")
  void testDuplicateActiveMatchReturns409WithMatchId() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto req = new CreateSoloMatchRequestDto();
    req.setMode(SoloModeDto.PRACTICE);
    req.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult firstRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID firstMatchId =
        UUID.fromString(JsonPath.read(firstRes.getResponse().getContentAsString(), "$.matchId"));

    // Gọi lại lần 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SOLO_MATCH_IN_PROGRESS"))
        .andExpect(jsonPath("$.matchId").value(firstMatchId.toString()));
  }

  @Test
  @DisplayName(
      "Xác thực skipRound (chỉ được skip ở mức 30s) và listenMore (không quá level 5, mismatch expectedLevel)")
  void testSkipAndListenMoreValidations() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto req = new CreateSoloMatchRequestDto();
    req.setMode(SoloModeDto.PRACTICE);
    req.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID matchId =
        UUID.fromString(JsonPath.read(res.getResponse().getContentAsString(), "$.matchId"));

    // 1. Skip ở level 1 -> 409 SKIP_NOT_ALLOWED
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/skip", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SKIP_NOT_ALLOWED"));

    // 2. listenMore với expectedLevel sai (đang ở level 1, next level là 2 nhưng gửi
    // expectedLevel=3)
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", matchId)
                .param("expectedLevel", "3")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LISTEN_LEVEL_MISMATCH"));

    // Nâng dần lên level 5
    for (int l = 2; l <= 5; l++) {
      mockMvc.perform(
          post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", matchId)
              .param("expectedLevel", String.valueOf(l))
              .with(jwt().jwt(j -> j.subject(userId.toString()))));
    }

    // 3. Đã ở level 5, gọi listenMore tiếp -> 409 LISTEN_LEVEL_MAX
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LISTEN_LEVEL_MAX"));
  }

  @Test
  @DisplayName("Tạo trận RANKED kèm bộ lọc filter ném lỗi 400 Bad Request theo OpenAPI & BR-23")
  void testRankedMatchWithFilterThrowsBadRequest400() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto req = new CreateSoloMatchRequestDto();
    req.setMode(SoloModeDto.RANKED);
    req.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);
    req.setFilter(new SoloFilterDto().genreCodes(List.of("pop")));

    mockMvc
        .perform(
            post("/api/v1/solo/matches")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName(
      "submitGuess với trackId không tồn tại trong catalog ném lỗi 404 TRACK_NOT_FOUND theo OpenAPI")
  void testSubmitGuessWithUnknownTrackIdThrowsNotFound404() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto req = new CreateSoloMatchRequestDto();
    req.setMode(SoloModeDto.PRACTICE);
    req.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID matchId =
        UUID.fromString(JsonPath.read(res.getResponse().getContentAsString(), "$.matchId"));

    UUID nonExistentTrackId = UUID.randomUUID();
    GuessRequestDto guessReq = new GuessRequestDto().trackId(nonExistentTrackId);

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guessReq)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRACK_NOT_FOUND"));
  }
}
