package com.meloo.game.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.meloo.IntegrationTest;
import com.meloo.audio.api.AudioPreparationResult;
import com.meloo.audio.api.AudioService;
import com.meloo.catalog.api.CatalogService;
import com.meloo.catalog.api.TrackInfo;
import com.meloo.game.events.MatchEndStatus;
import com.meloo.game.events.MatchFinished;
import com.meloo.game.events.MatchStarted;
import com.meloo.game.solo.SoloGameService;
import com.meloo.game.solo.SoloMatch;
import com.meloo.game.solo.SoloMatchManager;
import com.meloo.game.solo.SoloMatchStatus;
import com.meloo.game.solo.SoloRoundData;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    final java.util.concurrent.atomic.AtomicBoolean failSynchronousEvent =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    @org.springframework.context.event.EventListener
    void onSynchronousEvent(MatchFinished event) {
      if (failSynchronousEvent.get()) {
        throw new RuntimeException("Simulated publisher/transaction failure in event pipeline");
      }
    }

    @ApplicationModuleListener
    void onMatchFinished(MatchFinished event) {
      finishedEvents.add(event);
    }

    @ApplicationModuleListener
    void onMatchStarted(MatchStarted event) {
      startedEvents.add(event);
    }

    public void setFailSynchronousEvent(boolean fail) {
      this.failSynchronousEvent.set(fail);
    }

    public List<MatchFinished> getFinishedEvents() {
      return finishedEvents;
    }

    public List<MatchStarted> getStartedEvents() {
      return startedEvents;
    }

    public Optional<MatchFinished> findFinishedEvent(UUID matchId) {
      return finishedEvents.stream().filter(e -> e.matchId().equals(matchId)).findFirst();
    }

    public List<MatchFinished> findAllFinishedEvents(UUID matchId) {
      return finishedEvents.stream().filter(e -> e.matchId().equals(matchId)).toList();
    }

    public Optional<MatchStarted> findStartedEvent(UUID matchId) {
      return startedEvents.stream().filter(e -> e.matchId().equals(matchId)).findFirst();
    }

    public void clear() {
      finishedEvents.clear();
      startedEvents.clear();
      failSynchronousEvent.set(false);
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

  private final Map<UUID, TrackInfo> tracksById = new ConcurrentHashMap<>();
  private final List<TrackInfo> standardTracks = new ArrayList<>();

  @BeforeEach
  void setUp() {
    testClock.setNow(Instant.parse("2026-10-29T12:00:00Z"));
    testListener.clear();
    tracksById.clear();
    standardTracks.clear();

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

    when(audioService.isRoundPlayable(any())).thenReturn(true);
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
      "Integration 5 vòng: đúng 2s, thay bài audio lỗi giữ chuỗi combo, nghe thêm rồi đúng, bỏ qua 30s, sai hết lượt")
  void testFiveRoundCompleteGameFlowWithComboAndSkip() throws Exception {
    UUID userId = UUID.randomUUID();

    // Cấu hình trước: ở vòng 2, bài gốc (standardTracks[1]) bị lỗi audio 404, bài thay thế
    // standardTracks[5] phát thành công
    TrackInfo originalTrack2 = standardTracks.get(1);
    TrackInfo replacementTrack2 = standardTracks.get(5);
    when(audioService.prepareRoundAudio(any(), eq(originalTrack2.id()), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("Preview URL 404 from provider"));
    when(audioService.prepareRoundAudio(
            any(), eq(replacementTrack2.id()), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());

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
    // VÒNG 2: Thay bài do lỗi audio (BR-27), đoán đúng ở level 1 (2s) -> +1000 điểm cơ bản + 10%
    // combo = 1100 điểm
    // Tổng điểm: 1000 + 1100 = 2100, combo streak = 2! (CHỨNG MINH BR-27: thay bài KHÔNG làm đứt
    // chuỗi combo)
    // ==========================================
    GuessRequestDto guess2 = new GuessRequestDto().trackId(replacementTrack2.id());
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guess2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.end.result.baseScore").value(1000))
        .andExpect(jsonPath("$.end.result.comboPercent").value(10))
        .andExpect(jsonPath("$.end.result.roundScore").value(1100))
        .andExpect(jsonPath("$.end.result.totalScore").value(2100))
        .andExpect(jsonPath("$.end.result.comboStreak").value(2))
        .andExpect(jsonPath("$.end.nextRound.roundNo").value(3));

    // ==========================================
    // VÒNG 3: Nghe thêm lên level 2 (5s) rồi đoán đúng -> +800 cơ bản + 20% combo (streak 2) = 960
    // điểm
    // Tổng điểm: 2100 + 960 = 3060, combo streak = 3
    // ==========================================
    TrackInfo track3 = standardTracks.get(2);
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/listen-more", matchId)
                .param("expectedLevel", "2")
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value(2))
        .andExpect(jsonPath("$.listenSeconds").value(5));

    GuessRequestDto guess3 = new GuessRequestDto().trackId(track3.id());
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/3/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guess3)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.end.result.baseScore").value(800))
        .andExpect(jsonPath("$.end.result.comboPercent").value(20))
        .andExpect(jsonPath("$.end.result.roundScore").value(960))
        .andExpect(jsonPath("$.end.result.totalScore").value(3060))
        .andExpect(jsonPath("$.end.result.comboStreak").value(3))
        .andExpect(jsonPath("$.end.nextRound.roundNo").value(4));

    // ==========================================
    // VÒNG 4: Nghe thêm đến level 5 (30s) rồi bỏ qua (skip) -> 0 điểm, đứt combo (streak = 0)
    // ==========================================
    for (int l = 2; l <= 5; l++) {
      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/rounds/4/listen-more", matchId)
                  .param("expectedLevel", String.valueOf(l))
                  .with(jwt().jwt(j -> j.subject(userId.toString()))))
          .andExpect(status().isOk());
    }

    // Skip ở level 5
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/4/skip", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("nickname", "Thái"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.outcome").value("GAVE_UP"))
        .andExpect(jsonPath("$.result.roundScore").value(0))
        .andExpect(jsonPath("$.result.comboStreak").value(0))
        .andExpect(jsonPath("$.result.totalScore").value(3060))
        .andExpect(jsonPath("$.nextRound.roundNo").value(5));

    // ==========================================
    // VÒNG 5: Đoán sai 5 lần mở dần đến 30s -> WRONG_OUT, 0 điểm, trận xong
    // ==========================================
    TrackInfo wrongTrack = standardTracks.get(9);
    GuessRequestDto wrongGuess = new GuessRequestDto().trackId(wrongTrack.id());

    for (int guessCount = 1; guessCount <= 4; guessCount++) {
      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/rounds/5/guesses", matchId)
                  .with(jwt().jwt(j -> j.subject(userId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(wrongGuess)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.correct").value(false))
          .andExpect(jsonPath("$.roundEnded").value(false))
          .andExpect(jsonPath("$.round.level").value(guessCount + 1));
    }

    // Lần 5 đoán sai ở level 5 -> WRONG_OUT, hết trận
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/5/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongGuess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(false))
        .andExpect(jsonPath("$.roundEnded").value(true))
        .andExpect(jsonPath("$.end.result.outcome").value("WRONG_OUT"))
        .andExpect(jsonPath("$.end.result.roundScore").value(0))
        .andExpect(jsonPath("$.end.result.totalScore").value(3060))
        .andExpect(jsonPath("$.end.matchFinished").value(true));

    // Kiểm tra kết quả trận đấu qua API getMatchResult
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/result", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FINISHED"))
        .andExpect(jsonPath("$.totalScore").value(3060))
        .andExpect(jsonPath("$.correctCount").value(3))
        .andExpect(jsonPath("$.rounds.length()").value(5))
        .andExpect(jsonPath("$.rounds[0].comboStreak").value(1))
        .andExpect(jsonPath("$.rounds[1].comboStreak").value(2))
        .andExpect(jsonPath("$.rounds[2].comboStreak").value(3))
        .andExpect(jsonPath("$.rounds[3].comboStreak").value(0))
        .andExpect(jsonPath("$.rounds[4].comboStreak").value(0));

    // Kiểm tra kết quả riêng từng vòng qua getRoundResult
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/rounds/2/result", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comboStreak").value(2))
        .andExpect(jsonPath("$.answer.trackId").value(replacementTrack2.id().toString()));

    // Kiểm tra sự kiện MatchFinished phát ra qua listener giao dịch
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.findFinishedEvent(matchId)).isPresent());
    MatchFinished event = testListener.findFinishedEvent(matchId).get();
    assertThat(event.matchId()).isEqualTo(matchId);
    assertThat(event.mode()).isEqualTo("PRACTICE");
    assertThat(event.ranked()).isFalse();
    assertThat(event.status()).isEqualTo(MatchEndStatus.FINISHED);
    assertThat(event.roundCount()).isEqualTo(5);
    assertThat(event.rounds()).hasSize(5);

    // Vòng 2 ghi nhận đúng bài hát thay thế (provider & providerTrackId)
    assertThat(event.rounds().get(1).trackId()).isEqualTo(replacementTrack2.id());
    assertThat(event.rounds().get(1).provider()).isEqualTo("DEEZER");
  }

  @Test
  @DisplayName(
      "PRACTICE bộ lọc rỗng: gọi selectRandomTracks với filter=null từ toàn bộ kho nhạc (AC 2)")
  void testPracticeMatchWithEmptyFilterSelectsFromEntireCatalog() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto req = new CreateSoloMatchRequestDto();
    req.setMode(SoloModeDto.PRACTICE);
    req.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);
    req.setFilter(new SoloFilterDto()); // Filter rỗng

    mockMvc
        .perform(
            post("/api/v1/solo/matches")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.roundCount").value(5));

    // Khẳng định catalogService.selectRandomTracks được gọi với filter là null
    verify(catalogService).selectRandomTracks(isNull(), eq(5), any());
  }

  @Test
  @DisplayName("Tạo trận không filter và đoán sai: response không chứa trường tùy chọn null")
  void soloMatchAndWrongGuessResponsesOmitNullFields() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto request = new CreateSoloMatchRequestDto();
    request.setMode(SoloModeDto.PRACTICE);
    request.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.filter").doesNotExist())
            .andReturn();
    String createResponse = createResult.getResponse().getContentAsString();
    assertNoNullAnywhere(createResponse);

    UUID matchId = UUID.fromString(JsonPath.read(createResponse, "$.matchId"));
    GuessRequestDto wrongGuess = new GuessRequestDto().trackId(standardTracks.get(1).id());

    MvcResult guessResult =
        mockMvc
            .perform(
                post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                    .with(jwt().jwt(j -> j.subject(userId.toString())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(wrongGuess)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correct").value(false))
            .andExpect(jsonPath("$.roundEnded").value(false))
            .andExpect(jsonPath("$.round").exists())
            .andExpect(jsonPath("$.end").doesNotExist())
            .andReturn();

    assertNoNullAnywhere(guessResult.getResponse().getContentAsString());
  }

  @Test
  @DisplayName(
      "Resume sau 15 phút: giữ nguyên vòng, level, điểm và chuỗi combo; response không lộ đáp án (AC 7, NFR-07)")
  void testResumePreservesRoundAndLevelAndDoesNotLeakAnswer() throws Exception {
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

    // Hoàn thành vòng 1 ở 2s
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isOk());

    // Ở vòng 2, nghe thêm lên level 2 (5s)
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/listen-more", matchId)
                .param("expectedLevel", "2")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.level").value(2));

    // Người chơi rời đi và quay lại 15 phút sau (dưới 30 phút)
    testClock.advance(Duration.ofMinutes(15));

    MvcResult activeRes =
        mockMvc
            .perform(
                get("/api/v1/solo/matches/active")
                    .with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchId").value(matchId.toString()))
            .andExpect(jsonPath("$.totalScore").value(1000))
            .andExpect(jsonPath("$.comboStreak").value(1))
            .andExpect(jsonPath("$.currentRound.roundNo").value(2))
            .andExpect(jsonPath("$.currentRound.level").value(2))
            .andExpect(jsonPath("$.currentRound.listenSeconds").value(5))
            .andExpect(jsonPath("$.currentRound.canListenMore").value(true))
            .andExpect(jsonPath("$.currentRound.canSkip").value(false))
            .andReturn();

    // Khẳng định bảo mật NFR-07: RoundState và AudioClipDto tuyệt đối không lộ trackId, title hay
    // artistName
    String activeBody = activeRes.getResponse().getContentAsString();
    assertThat(activeBody).doesNotContain(standardTracks.get(1).id().toString());
    assertThat(activeBody).doesNotContain(standardTracks.get(1).title());
    assertThat(activeBody).doesNotContain(standardTracks.get(1).artistName());

    // Chơi tiếp vòng 2 với đúng bài gốc -> đoán đúng thành công
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(1).id()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.end.result.roundScore").value(880))
        .andExpect(jsonPath("$.end.result.totalScore").value(1880))
        .andExpect(jsonPath("$.end.result.comboStreak").value(2));
  }

  @Test
  @DisplayName(
      "Trận RANKED quá 30 phút thành ABANDONED: ranked chuyển false, rounds chỉ gồm vòng đã xong (AC 4, 7)")
  void testRankedMatchExpireAfter30Minutes() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto rankedReq = new CreateSoloMatchRequestDto();
    rankedReq.setMode(SoloModeDto.RANKED);
    rankedReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    MvcResult createRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(
                        jwt()
                            .jwt(
                                j ->
                                    j.subject(userId.toString()).claim("nickname", "RankedTester")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(rankedReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ranked").value(true))
            .andReturn();

    UUID matchId =
        UUID.fromString(JsonPath.read(createRes.getResponse().getContentAsString(), "$.matchId"));

    // Khẳng định MatchStarted được phát ra với ranked=true, mode=RANKED (AC 4, AC 8)
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.findStartedEvent(matchId)).isPresent());
    MatchStarted startEvent = testListener.findStartedEvent(matchId).get();
    assertThat(startEvent.ranked()).isTrue();
    assertThat(startEvent.roundCount()).isEqualTo(5);

    // Hoàn thành vòng 1
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isOk());

    // Hoàn thành vòng 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(1).id()))))
        .andExpect(status().isOk());

    // Vòng 3 đang dở, người chơi dừng 31 phút
    testClock.advance(Duration.ofMinutes(31));

    // getActive lúc này trả 404 NO_ACTIVE_MATCH và kích hoạt expire
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NO_ACTIVE_MATCH"));

    // Sự kiện MatchFinished được phát ra với trạng thái ABANDONED
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.findFinishedEvent(matchId)).isPresent());
    MatchFinished finishEvent = testListener.findFinishedEvent(matchId).get();
    assertThat(finishEvent.matchId()).isEqualTo(matchId);
    assertThat(finishEvent.status()).isEqualTo(MatchEndStatus.ABANDONED);
    assertThat(finishEvent.ranked()).isFalse(); // BR-23: quá hạn thì không tính xếp hạng
    assertThat(finishEvent.rounds())
        .hasSize(2); // Chỉ có vòng 1 và 2, vòng 3 dở KHÔNG nằm trong rounds!
    assertThat(finishEvent.players().get(0).totalScore()).isEqualTo(2100);
    assertThat(finishEvent.players().get(0).results().get(0).answerTimeMs()).isNotNull();
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
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", practiceMatchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isOk());

    // Bỏ trận ở vòng 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/abandon", practiceMatchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ABANDONED"))
        .andExpect(jsonPath("$.ranked").value(false))
        .andExpect(jsonPath("$.totalScore").value(1000))
        .andExpect(jsonPath("$.rounds.length()").value(1))
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
      "Kiểm tra biên roundNo (404 khi > roundCount, 400 khi <= 0) và 409 ROUND_NOT_ACTIVE khi gọi vòng cũ (M5, m6)")
  void testRoundNoBoundaryAndCompletedRoundReturns409() throws Exception {
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

    // 1. roundNo trong khoảng 1..15 nhưng vượt quá roundCount (5) của trận: round 6 -> 404
    // ROUND_NOT_FOUND (M5)
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/6/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"));

    // 2. roundNo vi phạm bean validation (@Min(1) / @Max(15)) -> 400 VALIDATION_FAILED (m6)
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/0/listen-more", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/99/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    // 3. Hoàn thành vòng 1 sang vòng 2
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isOk());

    // 4. Gọi lại vòng 1 đã kết thúc khi đang ở vòng 2 -> 409 ROUND_NOT_ACTIVE (M5)
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROUND_NOT_ACTIVE"));

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROUND_NOT_ACTIVE"));
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

  @Test
  @DisplayName(
      "Đồng thời: nhiều luồng cùng gọi listenMore với expectedLevel=2 -> đúng 1 luồng 200, các luồng khác 409 (M1)")
  void testConcurrencyListenMoreWithExpectedLevel() throws Exception {
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

    int threadCount = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Callable<Integer>> tasks = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      tasks.add(
          () -> {
            MvcResult mvcRes =
                mockMvc
                    .perform(
                        post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", matchId)
                            .param("expectedLevel", "2")
                            .with(jwt().jwt(j -> j.subject(userId.toString()))))
                    .andReturn();
            return mvcRes.getResponse().getStatus();
          });
    }

    List<Future<Integer>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    int okCount = 0;
    int conflictCount = 0;
    for (Future<Integer> f : futures) {
      int st = f.get();
      if (st == 200) {
        okCount++;
      } else if (st == 409) {
        conflictCount++;
      }
    }

    // Đúng 1 luồng thành công, các luồng còn lại nhận 409 LISTEN_LEVEL_MISMATCH
    assertThat(okCount).isEqualTo(1);
    assertThat(conflictCount).isEqualTo(threadCount - 1);
  }

  @Test
  @DisplayName(
      "Đồng thời: nhiều luồng cùng gọi abandonSoloMatch -> chỉ phát đúng 1 sự kiện MatchFinished (M2)")
  void testConcurrencyAbandonPublishesSingleMatchFinished() throws Exception {
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

    int threadCount = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Callable<Integer>> tasks = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      tasks.add(
          () -> {
            MvcResult mvcRes =
                mockMvc
                    .perform(
                        post("/api/v1/solo/matches/{matchId}/abandon", matchId)
                            .with(jwt().jwt(j -> j.subject(userId.toString()))))
                    .andReturn();
            return mvcRes.getResponse().getStatus();
          });
    }

    List<Future<Integer>> futures = executor.invokeAll(tasks);
    executor.shutdown();

    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.findFinishedEvent(matchId)).isPresent());

    // Khẳng định chỉ có duy nhất 1 sự kiện MatchFinished được phát ra
    assertThat(testListener.findAllFinishedEvents(matchId)).hasSize(1);
  }

  @Test
  @DisplayName(
      "Khi tất cả bài thay thế đều lỗi audio -> trả 422 NOT_ENOUGH_TRACKS và không làm hỏng trạng thái match (M4)")
  void testAllReplacementTracksFailThrowsNotEnoughTracks() throws Exception {
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

    // Cấu hình vòng 2: mọi bài audio đều lỗi
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("Provider CDN completely down"));

    // Hoàn thành vòng 1 -> chuẩn bị vòng 2 sẽ gặp lỗi thay bài quá số lần
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_TRACKS"));

    // Trạng thái trận không bị hỏng: kết quả vòng 1 vẫn lấy được chính xác
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/rounds/1/result", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundScore").value(1000));
  }

  @Test
  @DisplayName(
      "PRACTICE: Khi chuẩn bị vòng 2 lỗi rồi audio hồi phục -> getCurrentRound lấy vòng 2 và skipRound chơi tiếp bình thường (M4)")
  void testNextRoundPreparationFailureAndRecoveryPractice() throws Exception {
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

    // Audio sập khi chuẩn bị vòng 2
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("Provider CDN down"));

    // Đoán đúng vòng 1 -> 422 NOT_ENOUGH_TRACKS do vòng 2 không chuẩn bị được
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_TRACKS"));

    // Audio hồi phục
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());

    // Gọi getCurrentRound -> tự chuẩn bị lại vòng 2 và trả 200 OK
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/rounds/current", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundNo").value(2))
        .andExpect(jsonPath("$.level").value(1))
        .andExpect(jsonPath("$.canListenMore").value(true));

    // Nghe tiếp lên mức 5
    for (int lvl = 2; lvl <= 5; lvl++) {
      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/rounds/2/listen-more", matchId)
                  .param("expectedLevel", String.valueOf(lvl))
                  .with(jwt().jwt(j -> j.subject(userId.toString()))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.level").value(lvl));
    }

    // Bỏ qua vòng 2 thành công -> chuyển tiếp sang vòng 3
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/skip", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchFinished").value(false))
        .andExpect(jsonPath("$.nextRound.roundNo").value(3));
  }

  @Test
  @DisplayName(
      "RANKED: Khi chuẩn bị vòng 2 lỗi rồi audio hồi phục -> getActive lấy vòng 2, đoán tiếp thành công, không bị kẹt (M4)")
  void testNextRoundPreparationFailureAndRecoveryRanked() throws Exception {
    UUID userId = UUID.randomUUID();

    CreateSoloMatchRequestDto req = new CreateSoloMatchRequestDto();
    req.setMode(SoloModeDto.RANKED);
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

    // Audio sập khi chuẩn bị vòng 2
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("Provider CDN down"));

    // Đoán đúng vòng 1 -> 422 NOT_ENOUGH_TRACKS
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(0).id()))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_TRACKS"));

    // Khẳng định RANKED không thể abandon
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/abandon", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RANKED_MATCH_CANNOT_ABANDON"));

    // Audio hồi phục
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());

    // Người chơi resume qua getActiveSoloMatch -> trả 200 và vòng 2 đã sẵn sàng
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(matchId.toString()))
        .andExpect(jsonPath("$.currentRound.roundNo").value(2))
        .andExpect(jsonPath("$.currentRound.level").value(1));

    // Đoán đúng vòng 2 -> chơi tiếp bình thường, không bị kẹt
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/2/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GuessRequestDto().trackId(standardTracks.get(1).id()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.end.result.comboStreak").value(2));
  }

  @Test
  @DisplayName(
      "Thử lại skipRound ở vòng 1 sau khi chuẩn bị vòng 2 bị lỗi -> audio hồi phục trả về 200 OK với vòng kế tiếp (M4)")
  void testSkipRoundRetryAfterNextRoundPreparationFailure() throws Exception {
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

    // Nghe lên mức 5 ở vòng 1
    for (int lvl = 2; lvl <= 5; lvl++) {
      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", matchId)
                  .param("expectedLevel", String.valueOf(lvl))
                  .with(jwt().jwt(j -> j.subject(userId.toString()))))
          .andExpect(status().isOk());
    }

    // Audio sập khi chuẩn bị vòng 2
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.failure("Provider CDN down"));

    // Gọi skipRound(1) -> ném 422 NOT_ENOUGH_TRACKS do vòng 2 lỗi
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/skip", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("NOT_ENOUGH_TRACKS"));

    // Audio hồi phục
    when(audioService.prepareRoundAudio(any(), any(), anyString(), anyString()))
        .thenReturn(AudioPreparationResult.success());

    // Thử lại skipRound(1) -> chuẩn bị lại vòng 2 thành công và trả 200 OK
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/skip", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchFinished").value(false))
        .andExpect(jsonPath("$.nextRound.roundNo").value(2));
  }

  @Test
  @DisplayName(
      "expireInactiveMatches kiên cường khi publisher lỗi: scheduler không chết, retry ở lần quét sau và phát đúng 1 lần (M8)")
  void testExpireInactiveMatchesResilientToPublisherExceptionAndRetriesNextRun() throws Exception {
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

    // Tua thời gian quá 30 phút
    testClock.advance(Duration.ofMinutes(31));

    // Mô phỏng publisher/transaction ném lỗi thoáng qua
    testListener.setFailSynchronousEvent(true);

    // Chạy dọn dẹp: không ném exception ra ngoài scheduler
    soloGameService.expireInactiveMatches();

    SoloMatch match = matchManager.getMatchById(matchId).orElseThrow();
    assertThat(match.status()).isEqualTo(com.meloo.game.solo.SoloMatchStatus.ABANDONED);
    assertThat(match.isMatchFinishedPublished()).isFalse();
    assertThat(testListener.findAllFinishedEvents(matchId)).isEmpty();

    // Hồi phục publisher
    testListener.setFailSynchronousEvent(false);

    // Lần quét sau: phát lại sự kiện thành công
    soloGameService.expireInactiveMatches();

    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(testListener.findFinishedEvent(matchId)).isPresent());
    assertThat(testListener.findAllFinishedEvents(matchId)).hasSize(1);
    assertThat(match.isMatchFinishedPublished()).isTrue();

    // Tua tiếp 31 phút nữa -> purgeOldMatches dọn dẹp sạch match khỏi matchManager
    testClock.advance(Duration.ofMinutes(31));
    soloGameService.expireInactiveMatches();

    assertThat(matchManager.getMatchById(matchId)).isEmpty();
  }

  @Test
  @DisplayName(
      "toRoundStateDto tự động thay bài theo BR-27 nếu bài hiện tại của vòng active không phát được (m5, m16)")
  void testActiveRoundAudioFailureTriggersReplacement() throws Exception {
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

    SoloMatch match = matchManager.getActiveMatchByUserId(userId).orElseThrow();
    TrackInfo initialRound1Track = match.getCurrentRoundData().track();

    // Đoán đúng vòng 1 để tạo combo streak = 1
    GuessRequestDto guessReq = new GuessRequestDto();
    guessReq.setTrackId(initialRound1Track.id());
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guessReq)))
        .andExpect(status().isOk());

    assertThat(match.comboTracker().currentStreak()).isEqualTo(1);
    int scoreBefore = match.calculateTotalScore();
    TrackInfo initialRound2Track = match.getCurrentRoundData().track();

    // Bài của vòng 2 bị báo không thể phát khi resume
    when(audioService.isRoundPlayable(match.getCurrentRoundData().roundId())).thenReturn(false);
    when(audioService.prepareRoundAudio(
            eq(match.getCurrentRoundData().roundId()),
            eq(initialRound2Track.id()),
            anyString(),
            anyString()))
        .thenReturn(AudioPreparationResult.failure("Corrupted audio"));

    // Khi resume qua active match -> tự động đổi sang bài thay thế (BR-27)
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active").with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentRound.roundNo").value(2));

    // Assert: bài đã được đổi sang track khác, điểm và combo streak được bảo toàn (m16)
    TrackInfo replacedTrack = match.getCurrentRoundData().track();
    assertThat(replacedTrack.id()).isNotEqualTo(initialRound2Track.id());
    assertThat(match.comboTracker().currentStreak()).isEqualTo(1);
    assertThat(match.calculateTotalScore()).isEqualTo(scoreBefore);
  }

  @Test
  @DisplayName(
      "SoloGameExceptionHandler điền correlationId từ CorrelationIdFilter.REQUEST_ATTRIBUTE khi thiếu header (m9)")
  void testCorrelationIdInExceptionHandler() throws Exception {
    // Gửi request sai validation mà không có header X-Correlation-Id
    mockMvc
        .perform(
            post("/api/v1/solo/matches")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"INVALID_MODE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  @DisplayName(
      "Đồng thời: hai request submitGuess cùng vòng 1 song song -> không làm trùng vòng, roundNo liên tục (M9)")
  void testConcurrencyDuplicateGuessDoesNotDuplicateRounds() throws Exception {
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

    SoloMatch match = matchManager.getMatchById(matchId).orElseThrow();
    TrackInfo track1 = match.getCurrentRoundData().track();

    GuessRequestDto guessReq = new GuessRequestDto();
    guessReq.setTrackId(track1.id());
    String guessJson = objectMapper.writeValueAsString(guessReq);

    java.util.concurrent.CountDownLatch inRound2Audio = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch releaseAudio = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger callCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

    when(audioService.prepareRoundAudio(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              int c = callCount.incrementAndGet();
              if (c == 1) {
                inRound2Audio.countDown();
                releaseAudio.await(5, java.util.concurrent.TimeUnit.SECONDS);
              } else if (c == 2) {
                releaseAudio.countDown();
              }
              return AudioPreparationResult.success();
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Integer> f1 =
        executor.submit(
            () ->
                mockMvc
                    .perform(
                        post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(guessJson))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    // Chờ thread 1 đang chuẩn bị audio vòng 2 ngoài khóa
    assertThat(inRound2Audio.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

    // Thread 2 gửi request đoán trùng cùng lúc
    Future<Integer> f2 =
        executor.submit(
            () ->
                mockMvc
                    .perform(
                        post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(guessJson))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    assertThat(f1.get()).isEqualTo(200);
    assertThat(f2.get()).isEqualTo(200);
    executor.shutdown();

    // Phục hồi audioService mặc định
    when(audioService.prepareRoundAudio(any(), any(), any(), any()))
        .thenReturn(AudioPreparationResult.success());

    // Kiểm tra cấu trúc vòng của trận: không bị duplicate vòng 2
    assertThat(match.roundDataList()).hasSize(2);
    assertThat(match.currentRoundIndex()).isEqualTo(1);
    List<Integer> roundNos = match.roundDataList().stream().map(SoloRoundData::roundNo).toList();
    assertThat(roundNos).containsExactly(1, 2);

    // Kiểm tra audio của bản thua được giải phóng qua audioService.releaseRoundAudio (m20)
    org.mockito.ArgumentCaptor<UUID> releasedCaptor =
        org.mockito.ArgumentCaptor.forClass(UUID.class);
    verify(audioService, atLeast(2)).releaseRoundAudio(releasedCaptor.capture());
    UUID round1Id = match.roundDataList().get(0).roundId();
    UUID round2WinnerId = match.roundDataList().get(1).roundId();
    assertThat(releasedCaptor.getAllValues()).contains(round1Id);
    assertThat(releasedCaptor.getAllValues())
        .anyMatch(id -> !id.equals(round1Id) && !id.equals(round2WinnerId));

    // Chơi tiếp các vòng 2, 3, 4, 5 để đảm bảo thứ tự hoàn tất sạch sẽ
    for (int r = 2; r <= 5; r++) {
      TrackInfo currentTrack = match.getCurrentRoundData().track();
      GuessRequestDto nextGuess = new GuessRequestDto();
      nextGuess.setTrackId(currentTrack.id());

      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/rounds/{roundNo}/guesses", matchId, r)
                  .with(jwt().jwt(j -> j.subject(userId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(nextGuess)))
          .andExpect(status().isOk());
    }

    assertThat(match.status()).isEqualTo(SoloMatchStatus.FINISHED);
    List<Integer> finalRoundNos =
        match.roundDataList().stream().map(SoloRoundData::roundNo).toList();
    assertThat(finalRoundNos).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  @DisplayName(
      "Đồng thời: submitGuess và getCurrentRound song song -> không làm xáo trộn thứ tự vòng (M9)")
  void testConcurrencyGuessAndGetCurrentRoundDoesNotCorruptRoundOrder() throws Exception {
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

    SoloMatch match = matchManager.getMatchById(matchId).orElseThrow();
    TrackInfo track1 = match.getCurrentRoundData().track();

    GuessRequestDto guessReq = new GuessRequestDto();
    guessReq.setTrackId(track1.id());
    String guessJson = objectMapper.writeValueAsString(guessReq);

    java.util.concurrent.CountDownLatch inRound2Audio = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch releaseAudio = new java.util.concurrent.CountDownLatch(1);

    java.util.concurrent.atomic.AtomicInteger callCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

    when(audioService.prepareRoundAudio(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              int c = callCount.incrementAndGet();
              if (c == 1) {
                inRound2Audio.countDown();
                releaseAudio.await(5, java.util.concurrent.TimeUnit.SECONDS);
              } else if (c == 2) {
                releaseAudio.countDown();
              }
              return AudioPreparationResult.success();
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Integer> f1 =
        executor.submit(
            () ->
                mockMvc
                    .perform(
                        post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                            .with(jwt().jwt(j -> j.subject(userId.toString())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(guessJson))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    assertThat(inRound2Audio.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

    Future<Integer> f2 =
        executor.submit(
            () ->
                mockMvc
                    .perform(
                        get("/api/v1/solo/matches/{matchId}/rounds/current", matchId)
                            .with(jwt().jwt(j -> j.subject(userId.toString()))))
                    .andReturn()
                    .getResponse()
                    .getStatus());

    assertThat(f1.get()).isEqualTo(200);
    assertThat(f2.get()).isEqualTo(200);
    executor.shutdown();

    when(audioService.prepareRoundAudio(any(), any(), any(), any()))
        .thenReturn(AudioPreparationResult.success());

    assertThat(match.roundDataList()).hasSize(2);
    assertThat(match.currentRoundIndex()).isEqualTo(1);
    List<Integer> roundNos = match.roundDataList().stream().map(SoloRoundData::roundNo).toList();
    assertThat(roundNos).containsExactly(1, 2);

    // Kiểm tra audio của bản thua được giải phóng qua audioService.releaseRoundAudio (m20)
    org.mockito.ArgumentCaptor<UUID> releasedCaptor =
        org.mockito.ArgumentCaptor.forClass(UUID.class);
    verify(audioService, atLeast(2)).releaseRoundAudio(releasedCaptor.capture());
    UUID round1Id = match.roundDataList().get(0).roundId();
    UUID round2WinnerId = match.roundDataList().get(1).roundId();
    assertThat(releasedCaptor.getAllValues()).contains(round1Id);
    assertThat(releasedCaptor.getAllValues())
        .anyMatch(id -> !id.equals(round1Id) && !id.equals(round2WinnerId));
  }

  @Test
  @DisplayName(
      "expireInactiveMatches phát lại MatchFinished cho trận FINISHED khi publish lúc xong trận gặp lỗi (m12)")
  void testExpireInactiveMatchesRetriesFinishedMatchWhenPublisherFails() throws Exception {
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

    SoloMatch match = matchManager.getMatchById(matchId).orElseThrow();

    // Chơi qua 4 vòng đầu
    for (int r = 1; r <= 4; r++) {
      TrackInfo currentTrack = match.getCurrentRoundData().track();
      GuessRequestDto guess = new GuessRequestDto();
      guess.setTrackId(currentTrack.id());

      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/rounds/{roundNo}/guesses", matchId, r)
                  .with(jwt().jwt(j -> j.subject(userId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(guess)))
          .andExpect(status().isOk());
    }

    // Ở vòng 5: Giả lập lỗi publisher khi kết thúc trận
    testListener.setFailSynchronousEvent(true);

    TrackInfo round5Track = match.getCurrentRoundData().track();
    GuessRequestDto round5Guess = new GuessRequestDto();
    round5Guess.setTrackId(round5Track.id());

    // Request vẫn thành công trả 200 kết quả trận
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/5/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(round5Guess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundEnded").value(true))
        .andExpect(jsonPath("$.end.matchFinished").value(true));

    assertThat(match.status()).isEqualTo(SoloMatchStatus.FINISHED);
    assertThat(match.isMatchFinishedPublished()).isFalse();

    // Hạ cờ lỗi và cho bộ dọn quét lại -> retry phát MatchFinished
    testListener.setFailSynchronousEvent(false);
    soloGameService.expireInactiveMatches();

    assertThat(match.isMatchFinishedPublished()).isTrue();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(testListener.findFinishedEvent(matchId)).isPresent());

    // Quá hạn 30 phút -> purgeOldMatches dọn trận đã publish
    testClock.advance(Duration.ofMinutes(35));
    soloGameService.expireInactiveMatches();

    assertThat(matchManager.getMatchById(matchId)).isEmpty();
  }

  @Test
  @DisplayName(
      "Phát lại lượt đoán đúng trên vòng đã kết thúc giữ đúng rightArtist = false theo BR-06 (m17)")
  void testReplayedGuessPreservesRightArtist() throws Exception {
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

    SoloMatch match = matchManager.getMatchById(matchId).orElseThrow();
    TrackInfo track1 = match.getCurrentRoundData().track();

    GuessRequestDto guessReq = new GuessRequestDto();
    guessReq.setTrackId(track1.id());

    // Giả lập lỗi chuẩn bị audio vòng 2 để vòng 1 kết thúc nhưng trận không chuyển sang vòng 2 (M4)
    when(audioService.prepareRoundAudio(any(), any(), any(), any()))
        .thenReturn(AudioPreparationResult.failure("Mock error"));

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guessReq)))
        .andExpect(status().isUnprocessableEntity());

    // Audio sẵn sàng trở lại
    when(audioService.prepareRoundAudio(any(), any(), any(), any()))
        .thenReturn(AudioPreparationResult.success());

    // Phát lại lượt đoán trên vòng 1 đã kết thúc -> rightArtist vẫn là false theo BR-06 (m17)
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", matchId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guessReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.correct").value(true))
        .andExpect(jsonPath("$.rightArtist").value(false));
  }

  @Test
  @DisplayName(
      "BE-RL-01: Exceeding create-match limit returns 429 with Retry-After, isolates players, and resets after clock advances")
  void createMatch_rateLimit_enforcesLimit_isolatesUsers_refillsAfterWait() throws Exception {
    UUID player1 = UUID.randomUUID();
    UUID player2 = UUID.randomUUID();

    CreateSoloMatchRequestDto createReq = new CreateSoloMatchRequestDto();
    createReq.setMode(SoloModeDto.PRACTICE);
    createReq.setRoundCount(CreateSoloMatchRequestDto.RoundCountEnum.NUMBER_5);

    // Create and abandon 10 matches consecutively (consuming all 10 create-match tokens for player
    // 1)
    for (int i = 0; i < 10; i++) {
      MvcResult res =
          mockMvc
              .perform(
                  post("/api/v1/solo/matches")
                      .with(jwt().jwt(j -> j.subject(player1.toString()).claim("nickname", "P1")))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(createReq)))
              .andExpect(status().isCreated())
              .andReturn();

      String matchIdStr = JsonPath.read(res.getResponse().getContentAsString(), "$.matchId");
      mockMvc
          .perform(
              post("/api/v1/solo/matches/{matchId}/abandon", matchIdStr)
                  .with(jwt().jwt(j -> j.subject(player1.toString()).claim("nickname", "P1"))))
          .andExpect(status().isOk());
    }

    // 11th call exceeds create match rate limit (10 req / 1 min) -> 429 Too Many Requests
    MvcResult rateLimitedRes =
        mockMvc
            .perform(
                post("/api/v1/solo/matches")
                    .with(jwt().jwt(j -> j.subject(player1.toString()).claim("nickname", "P1")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.title").value("Too Many Requests"))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.instance").value("/api/v1/solo/matches"))
            .andReturn();

    String retryAfterHeader = rateLimitedRes.getResponse().getHeader("Retry-After");
    assertThat(retryAfterHeader).isNotNull();
    assertThat(Long.parseLong(retryAfterHeader)).isGreaterThanOrEqualTo(1L);

    // Another player (player 2) is unaffected and creates match successfully
    mockMvc
        .perform(
            post("/api/v1/solo/matches")
                .with(jwt().jwt(j -> j.subject(player2.toString()).claim("nickname", "P2")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());

    // After waiting period (clock advanced by 1 minute), player 1 can create match again
    testClock.advance(Duration.ofMinutes(1));

    mockMvc
        .perform(
            post("/api/v1/solo/matches")
                .with(jwt().jwt(j -> j.subject(player1.toString()).claim("nickname", "P1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName(
      "BE-RL-01: Exceeding action quota (60/min) returns 429 and refills after clock advances")
  void actionRateLimit_enforcesLimit_andRefillsAfterWait() throws Exception {
    UUID playerId = UUID.randomUUID();

    // 60 allowed getActiveSoloMatch requests
    for (int i = 0; i < 60; i++) {
      mockMvc
          .perform(
              get("/api/v1/solo/matches/active")
                  .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
          .andExpect(status().isNotFound()); // NO_ACTIVE_MATCH
    }

    // 61st request exceeds action limit
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active")
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    // Advance clock by 1 minute
    testClock.advance(Duration.ofMinutes(1));

    // Request succeeds again
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active")
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "BE-RL-01: Exceeding action quota blocks all remaining in-match action endpoints with 429")
  void actionRateLimit_blocksAllActionEndpointsWhenExhausted() throws Exception {
    UUID playerId = UUID.randomUUID();
    UUID dummyMatchId = UUID.randomUUID();

    // Consume all 60 action tokens
    for (int i = 0; i < 60; i++) {
      mockMvc
          .perform(
              get("/api/v1/solo/matches/active")
                  .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
          .andExpect(status().isNotFound());
    }

    // Next request to getActiveSoloMatch exceeds action quota -> 429
    mockMvc
        .perform(
            get("/api/v1/solo/matches/active")
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    // Verify all other 7 action endpoints are also protected by checkAction
    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/rounds/current", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/listen-more", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/skip", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    GuessRequestDto guessDto = new GuessRequestDto();
    guessDto.setTrackId(UUID.randomUUID());
    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/rounds/1/guesses", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(guessDto)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/rounds/1/result", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    mockMvc
        .perform(
            get("/api/v1/solo/matches/{matchId}/result", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

    mockMvc
        .perform(
            post("/api/v1/solo/matches/{matchId}/abandon", dummyMatchId)
                .with(jwt().jwt(j -> j.subject(playerId.toString()).claim("nickname", "P"))))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  private static void assertNoNullAnywhere(String json) {
    Object root = JsonPath.read(json, "$");
    assertNoNullAnywhere((Object) root);
  }

  private static void assertNoNullAnywhere(Object value) {
    assertThat(value).isNotNull();
    if (value instanceof Map<?, ?> object) {
      object.values().forEach(child -> assertNoNullAnywhere(child));
    } else if (value instanceof Iterable<?> array) {
      array.forEach(child -> assertNoNullAnywhere(child));
    }
  }
}
