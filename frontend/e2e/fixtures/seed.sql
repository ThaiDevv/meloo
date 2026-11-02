-- Small fixed seed dataset for E2E testing (FakeMusicProvider & sample mp3s)
-- Idempotent insertions with ON CONFLICT

-- 1. Genres
INSERT INTO genre (id, code, name_vi, name_en) VALUES
  ('11111111-1111-1111-1111-111111111111', 'vpop', 'V-Pop', 'V-Pop'),
  ('11111111-1111-1111-1111-111111111112', 'usuk-pop', 'Pop Âu Mỹ', 'US-UK Pop'),
  ('11111111-1111-1111-1111-111111111113', 'kpop', 'K-Pop', 'K-Pop')
ON CONFLICT (id) DO NOTHING;

-- 2. Artists
INSERT INTO artist (id, name, name_normalized, provider, provider_artist_id) VALUES
  ('22222222-2222-2222-2222-222222222201', 'Sơn Tùng M-TP', 'son tung m tp', 'FAKE', 'art-1')
ON CONFLICT (provider, provider_artist_id) DO UPDATE SET
  name = EXCLUDED.name,
  name_normalized = EXCLUDED.name_normalized;

-- 3. Tracks (maps to FakeMusicProvider: fake-1..fake-6)
-- All 5 tracks in the standard playlist share the title "Chúng Ta Của Hiện Tại"
-- so that regardless of ORDER BY RANDOM() selection, the round answer is deterministic for E2E.
-- fake-6 is a decoy with title "Chúng Ta Của Tương Lai" for testing wrong guesses and right-artist hint.
INSERT INTO track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, genre_id, country_code, release_year, popularity_rank, cover_url, enabled) VALUES
  ('33333333-3333-3333-3333-333333333301', 'FAKE', 'fake-1', 'Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2020, 1, 'https://fake-media.meloo.internal/covers/see-tinh.jpg', true),
  ('33333333-3333-3333-3333-333333333302', 'FAKE', 'fake-2', 'Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2020, 2, NULL, true),
  ('33333333-3333-3333-3333-333333333303', 'FAKE', 'fake-3', 'Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2020, 3, 'https://fake-media.meloo.internal/covers/cat-doi-noi-sau.jpg', true),
  ('33333333-3333-3333-3333-333333333304', 'FAKE', 'fake-4', 'Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2020, 4, NULL, true),
  ('33333333-3333-3333-3333-333333333305', 'FAKE', 'fake-5', 'Chúng Ta Của Hiện Tại', 'chung ta cua hien tai', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2020, 5, 'https://fake-media.meloo.internal/covers/dynamite.jpg', true),
  ('33333333-3333-3333-3333-333333333306', 'FAKE', 'fake-6', 'Chúng Ta Của Tương Lai', 'chung ta cua tuong lai', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2024, 6, 'https://fake-media.meloo.internal/covers/noi-nay-co-anh.jpg', true)
ON CONFLICT (provider, provider_track_id) DO UPDATE SET
  title = EXCLUDED.title,
  title_normalized = EXCLUDED.title_normalized,
  primary_artist_id = EXCLUDED.primary_artist_id,
  genre_id = EXCLUDED.genre_id,
  country_code = EXCLUDED.country_code,
  release_year = EXCLUDED.release_year,
  popularity_rank = EXCLUDED.popularity_rank,
  cover_url = EXCLUDED.cover_url,
  enabled = EXCLUDED.enabled;

-- 4. Standard Playlist (for Ranked matches)
INSERT INTO playlist (id, code, name_vi, name_en, kind, enabled) VALUES
  ('44444444-4444-4444-4444-444444444444', 'standard', 'Bộ chuẩn Meloo', 'Meloo Standard Set', 'STANDARD', true)
ON CONFLICT (code) DO NOTHING;

-- 5. Playlist Tracks (strictly 5 tracks in playlist so exactly 5 rounds are formed)
DELETE FROM playlist_track WHERE playlist_id = '44444444-4444-4444-4444-444444444444';
INSERT INTO playlist_track (playlist_id, track_id, position) VALUES
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333301', 1),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333302', 2),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333303', 3),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333304', 4),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333305', 5)
ON CONFLICT (playlist_id, track_id) DO NOTHING;
