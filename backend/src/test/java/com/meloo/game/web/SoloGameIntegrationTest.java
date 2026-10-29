package com.meloo.game.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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
}
