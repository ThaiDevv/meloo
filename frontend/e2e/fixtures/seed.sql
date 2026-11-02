-- Small fixed seed dataset for E2E testing (FakeMusicProvider & sample mp3s)
-- Idempotent insertions with ON CONFLICT DO NOTHING

-- 1. Genres
INSERT INTO genre (id, code, name_vi, name_en) VALUES
  ('11111111-1111-1111-1111-111111111111', 'vpop', 'V-Pop', 'V-Pop'),
  ('11111111-1111-1111-1111-111111111112', 'usuk-pop', 'Pop Âu Mỹ', 'US-UK Pop'),
  ('11111111-1111-1111-1111-111111111113', 'kpop', 'K-Pop', 'K-Pop')
ON CONFLICT (id) DO NOTHING;

-- 2. Artists
INSERT INTO artist (id, name, name_normalized, provider, provider_artist_id) VALUES
  ('22222222-2222-2222-2222-222222222201', 'Hoàng Thùy Linh', 'hoang thuy linh', 'FAKE', 'art-1'),
  ('22222222-2222-2222-2222-222222222202', 'MONO', 'mono', 'FAKE', 'art-2'),
  ('22222222-2222-2222-2222-222222222203', 'Tăng Duy Tân', 'tang duy tan', 'FAKE', 'art-3'),
  ('22222222-2222-2222-2222-222222222204', 'Ed Sheeran', 'ed sheeran', 'FAKE', 'art-4'),
  ('22222222-2222-2222-2222-222222222205', 'BTS', 'bts', 'FAKE', 'art-5'),
  ('22222222-2222-2222-2222-222222222206', 'Sơn Tùng M-TP', 'son tung m tp', 'FAKE', 'art-6')
ON CONFLICT (provider, provider_artist_id) DO NOTHING;

-- 3. Tracks (maps to FakeMusicProvider & sample1.mp3, sample2.mp3, sample3.mp3)
INSERT INTO track (id, provider, provider_track_id, title, title_normalized, primary_artist_id, genre_id, country_code, release_year, popularity_rank, cover_url, enabled) VALUES
  ('33333333-3333-3333-3333-333333333301', 'FAKE', 'fake-1', 'See Tình', 'see tinh', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111111', 'VN', 2022, 1, 'https://fake-media.meloo.internal/covers/see-tinh.jpg', true),
  ('33333333-3333-3333-3333-333333333302', 'FAKE', 'fake-2', 'Waiting For You', 'waiting for you', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111111', 'VN', 2022, 2, NULL, true),
  ('33333333-3333-3333-3333-333333333303', 'FAKE', 'fake-3', 'Cắt Đôi Nỗi Sầu', 'cat doi noi sau', '22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111111', 'VN', 2023, 3, 'https://fake-media.meloo.internal/covers/cat-doi-noi-sau.jpg', true),
  ('33333333-3333-3333-3333-333333333304', 'FAKE', 'fake-4', 'Shape of You', 'shape of you', '22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111112', 'US', 2017, 4, NULL, true),
  ('33333333-3333-3333-3333-333333333305', 'FAKE', 'fake-5', 'Dynamite', 'dynamite', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111113', 'KR', 2020, 5, 'https://fake-media.meloo.internal/covers/dynamite.jpg', true),
  ('33333333-3333-3333-3333-333333333306', 'FAKE', 'fake-6', 'Nơi Này Có Anh', 'noi nay co anh', '22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111111', 'VN', 2017, 6, 'https://fake-media.meloo.internal/covers/noi-nay-co-anh.jpg', true)
ON CONFLICT (provider, provider_track_id) DO NOTHING;

-- 4. Standard Playlist (for Ranked matches)
INSERT INTO playlist (id, code, name_vi, name_en, kind, enabled) VALUES
  ('44444444-4444-4444-4444-444444444444', 'standard', 'Bộ chuẩn Meloo', 'Meloo Standard Set', 'STANDARD', true)
ON CONFLICT (code) DO NOTHING;

-- 5. Playlist Tracks
INSERT INTO playlist_track (playlist_id, track_id, position) VALUES
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333301', 1),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333302', 2),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333303', 3),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333304', 4),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333305', 5),
  ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333306', 6)
ON CONFLICT (playlist_id, track_id) DO NOTHING;
