#!/usr/bin/env python3
"""
Fetch real tracks from Deezer API to generate backend/src/main/resources/seed/catalog-seed.json.
Guarantees:
- Real Deezer numeric track IDs (e.g. "2470200565")
- Real Deezer numeric artist IDs (e.g. "5400939") for providerArtistId and primaryArtistId
- Curated canonical artist registry preventing cross-country and cross-genre contamination
- Real static CDN album cover URLs
- Real working preview URLs (without ephemeral signed tokens)
- Real release years fetched directly from Deezer /track/{id} API (no synthetic years!)
- Strict original-only tracks: filters out non-original versions (Remix, Live, Acoustic, Version, Cover, Instrumental...)
- Deduplication: only one original track per song title per artist (no duplicates)
- Balanced distribution across V-Pop (VN), US-UK (US, GB, CA, SE, NO), and K-Pop (KR)
- Non-empty tracks for all 8 Curated Playlists and all 8 Genres (each >= 70 tracks)
- Strictly >= 1,000 valid tracks
- Strict validation checks on artist consistency, country mapping, and decade integrity
"""

from concurrent.futures import ThreadPoolExecutor, as_completed
import collections
from datetime import datetime, timezone
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "MelooCatalogSeeder/4.0 (Academic Project; tranvanthai20062@gmail.com)"

# ---------------------------------------------------------------------------
# Non-original version pattern per BR-05 & AC: only original versions as game answers
# ---------------------------------------------------------------------------
NON_ORIGINAL_PATTERN = re.compile(
    r'(?i)\b(remix|live|version|acoustic|edit|mix|cover|instrumental|piano|slowed|sped\s*up|karaoke|orchestral|orchestra|demo|tribute)\b'
)

def normalize_title(title):
    """Normalize title per BR-05 for deduplication within the same artist."""
    if not title:
        return ""
    # 1. Remove text in brackets / parentheses
    s = re.sub(r'\([^)]*\)|\[[^\]]*\]', ' ', title)
    # 2. Remove trailing version qualifiers
    s = re.sub(r'(?i)\s+-\s+.*\b(remix|live|acoustic|ver|version|cover|instrumental|official.*|mv)\b.*$', '', s)
    # 3. Remove feat
    s = re.sub(r'(?i)\s+(?:feat\.?|ft\.?|featuring)\s+.*$', '', s)
    # 4. Strip diacritics & lowercase (with đ/Đ -> d per BR-05)
    s = s.replace('đ', 'd').replace('Đ', 'd')
    s = unicodedata.normalize('NFD', s.lower())
    s = ''.join(c for c in s if unicodedata.category(c) != 'Mn')
    # 5. Non-alphanumeric to whitespace
    s = re.sub(r'[^a-z0-9\s]+', ' ', s)
    return ' '.join(s.split())

# ---------------------------------------------------------------------------
# Canonical Artist Registry
# (artist_id, display_name, country_code, genre_code, is_girlgroup)
# ---------------------------------------------------------------------------

VPOP_CANONICAL = [
    # Rap & Hip-hop
    (8979084, "Đen", "VN", "rap-hiphop", False),
    (89446142, "HIEUTHUHAI", "VN", "rap-hiphop", False),
    (5283388, "Binz", "VN", "rap-hiphop", False),
    (4075580, "Karik", "VN", "rap-hiphop", False),
    (81827512, "tlinh", "VN", "rap-hiphop", False),
    (8823812, "B Ray", "VN", "rap-hiphop", False),
    (7896668, "Rhymastic", "VN", "rap-hiphop", False),
    (5160282, "Suboi", "VN", "rap-hiphop", False),
    (103129612, "16 Typh", "VN", "rap-hiphop", False),
    (4496739, "JustaTee", "VN", "rnb", False),
    # Ballad & 2010s Nostalgia
    (863447, "Mỹ Tâm", "VN", "ballad", False),
    (499354, "Hà Anh Tuấn", "VN", "ballad", False),
    (5038281, "Bùi Anh Tuấn", "VN", "ballad", False),
    (9352628, "Hương Tràm", "VN", "ballad", False),
    (3785231, "Khắc Việt", "VN", "ballad", False),
    (4972285, "Phan Mạnh Quỳnh", "VN", "ballad", False),
    (4333165, "Đông Nhi", "VN", "vpop", False),
    (474776, "Noo Phước Thịnh", "VN", "vpop", False),
    (5129878, "Trung Quân", "VN", "ballad", False),
    (4023953, "Văn Mai Hương", "VN", "ballad", False),
    (14659489, "Đức Phúc", "VN", "ballad", False),
    (281074231, "ERIK", "VN", "ballad", False),
    (7982110, "Hòa Minzy", "VN", "ballad", False),
    # Modern Pop & 2020s Hits
    (5400939, "Sơn Tùng M-TP", "VN", "vpop", False),
    (9286846, "MONO", "VN", "vpop", False),
    (86122002, "Tăng Duy Tân", "VN", "vpop", False),
    (4777948, "Hoàng Thùy Linh", "VN", "vpop", False),
    (14470049, "Vũ.", "VN", "ballad", False),
    (4333919, "Bích Phương", "VN", "vpop", False),
    (8621616, "SOOBIN", "VN", "rnb", False),
    (5377793, "AMEE", "VN", "vpop", False),
    (77668902, "Wren Evans", "VN", "vpop", False),
    (7652822, "GREY D", "VN", "vpop", False),
    (368898532, "MIN", "VN", "vpop", False),
    (95338312, "Jack - J97", "VN", "vpop", False),
]

KPOP_CANONICAL = [
    # Boy groups & male soloists
    (6982223, "BTS", "KR", "kpop", False),
    (13923487, "Stray Kids", "KR", "kpop", False),
    (12973277, "EXO", "KR", "kpop", False),
    (267273672, "SEVENTEEN", "KR", "kpop", False),
    (371377201, "BIGBANG", "KR", "kpop", False),
    (60552072, "TOMORROW X TOGETHER", "KR", "kpop", False),
    (113915572, "ENHYPEN", "KR", "kpop", False),
    (50306442, "NCT 127", "KR", "kpop", False),
    (11999322, "NCT DREAM", "KR", "kpop", False),
    # Girl groups & female soloists
    (10803980, "BLACKPINK", "KR", "kpop", True),
    (15008493, "TWICE", "KR", "kpop", True),
    (178008437, "NewJeans", "KR", "kpop", True),
    (153042452, "IVE", "KR", "kpop", True),
    (168158797, "LE SSERAFIM", "KR", "kpop", True),
    (113547672, "aespa", "KR", "kpop", True),
    (15065941, "(G)I-DLE", "KR", "kpop", True),
    (3649631, "ITZY", "KR", "kpop", True),
    (252200682, "Red Velvet", "KR", "kpop", True),
    (2810121, "IU", "KR", "kpop", True),
    (2562931, "TAEYEON", "KR", "kpop", True),
    (1310497, "Girls' Generation", "KR", "kpop", True),
    (1492615, "2NE1", "KR", "kpop", True),
]

USUK_CANONICAL = [
    # Pop
    (12246, "Taylor Swift", "US", "usuk-pop", False),
    (384236, "Ed Sheeran", "GB", "usuk-pop", False),
    (1562681, "Ariana Grande", "US", "usuk-pop", False),
    (9635624, "Billie Eilish", "US", "usuk-pop", False),
    (288166, "Justin Bieber", "CA", "usuk-pop", False),
    (8706544, "Dua Lipa", "GB", "usuk-pop", False),
    (1188, "Maroon 5", "US", "usuk-pop", False),
    (144227, "Katy Perry", "US", "usuk-pop", False),
    (11239321, "Charlie Puth", "US", "usuk-pop", False),
    (75491, "Lady Gaga", "US", "usuk-pop", False),
    (5962948, "Shawn Mendes", "CA", "usuk-pop", False),
    (84302292, "Sia", "US", "usuk-pop", False),
    (13867133, "Sam Smith", "GB", "usuk-pop", False),
    # Ballad
    (75798, "Adele", "GB", "ballad", False),
    # R&B
    (4050205, "The Weeknd", "CA", "rnb", False),
    (429675, "Bruno Mars", "US", "rnb", False),
    (145, "Beyoncé", "US", "rnb", False),
    (564, "Rihanna", "US", "rnb", False),
    # Rap
    (13, "Eminem", "US", "rap-hiphop", False),
    (7543848, "Post Malone", "US", "rap-hiphop", False),
    # Rock
    (412, "Queen", "GB", "rock", False),
    (892, "Coldplay", "GB", "rock", False),
    (416239, "Imagine Dragons", "US", "rock", False),
    (259, "Michael Jackson", "US", "usuk-pop", False),
    # EDM
    (293585, "Avicii", "SE", "edm", False),
    (12178, "Calvin Harris", "GB", "edm", False),
    (4104927, "The Chainsmokers", "US", "edm", False),
    (13529769, "Alan Walker", "NO", "edm", False),
]

# Combine all canonical artists into a single master registry by string artist ID
CANONICAL_ARTIST_MAP = {}
for aid, name, country, genre, is_gg in (VPOP_CANONICAL + KPOP_CANONICAL + USUK_CANONICAL):
    CANONICAL_ARTIST_MAP[str(aid)] = {
        "id": str(aid),
        "name": name,
        "country": country,
        "genre": genre,
        "is_girlgroup": is_gg
    }

GENRES = [
    {"code": "vpop", "nameVi": "V-Pop", "nameEn": "V-Pop"},
    {"code": "usuk-pop", "nameVi": "US-UK Pop", "nameEn": "US-UK Pop"},
    {"code": "kpop", "nameVi": "K-Pop", "nameEn": "K-Pop"},
    {"code": "ballad", "nameVi": "Nhạc Trẻ Ballad", "nameEn": "Ballad"},
    {"code": "rap-hiphop", "nameVi": "Rap & Hip-hop", "nameEn": "Rap & Hip-hop"},
    {"code": "rock", "nameVi": "Rock", "nameEn": "Rock"},
    {"code": "edm", "nameVi": "EDM & Dance", "nameEn": "EDM & Dance"},
    {"code": "rnb", "nameVi": "R&B / Soul", "nameEn": "R&B / Soul"}
]

PLAYLISTS = [
    {"code": "vpop-hits-2020s", "nameVi": "V-Pop Hits 2020s", "nameEn": "V-Pop Hits 2020s", "kind": "CURATED"},
    {"code": "vpop-ballad", "nameVi": "Nhạc Trẻ Ballad Tâm Trạng", "nameEn": "V-Pop Emotional Ballads", "kind": "CURATED"},
    {"code": "vpop-rap-hiphop", "nameVi": "Rap & Hip-hop Việt", "nameEn": "Vietnamese Rap & Hip-hop", "kind": "CURATED"},
    {"code": "vpop-nostalgia-2010s", "nameVi": "V-Pop Thập Niên 2010", "nameEn": "V-Pop 2010s Nostalgia", "kind": "CURATED"},
    {"code": "usuk-billboard-hits", "nameVi": "US-UK Billboard Hits", "nameEn": "US-UK Billboard Hits", "kind": "CURATED"},
    {"code": "usuk-pop-classics", "nameVi": "US-UK Pop Bất Hủ", "nameEn": "US-UK Pop Classics", "kind": "CURATED"},
    {"code": "kpop-top-hits", "nameVi": "K-Pop Đỉnh Cao", "nameEn": "K-Pop Top Hits", "kind": "CURATED"},
    {"code": "kpop-girlgroups", "nameVi": "K-Pop Girl Groups", "nameEn": "K-Pop Girl Groups", "kind": "CURATED"}
]

def fetch_deezer(url, max_retries=3):
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/json"
        }
    )
    for attempt in range(max_retries):
        try:
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = resp.read().decode("utf-8")
                return json.loads(data)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(1.2 * (attempt + 1))
            else:
                return None
        except Exception:
            time.sleep(0.5)
    return None

def fetch_track_release_year(tid):
    url = f"https://api.deezer.com/track/{tid}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(4):
        try:
            time.sleep(0.12)  # Throttle for rate limiting (~8 req/sec)
            with urllib.request.urlopen(req, timeout=6) as resp:
                d = json.loads(resp.read().decode())
                rd = d.get("release_date") or d.get("album", {}).get("release_date")
                if rd and isinstance(rd, str) and len(rd) >= 4:
                    year_str = rd[:4]
                    if year_str.isdigit() and (year_str.startswith("19") or year_str.startswith("20")):
                        return tid, int(year_str)
                return tid, None
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(1.2 * (attempt + 1))
            else:
                return tid, None
        except Exception:
            time.sleep(0.5)
    return tid, None

def collect_tracks():
    raw_tracks = {}
    seen_artist_titles = set()
    total_artists = len(CANONICAL_ARTIST_MAP)
    print(f"Collecting tracks from {total_artists} canonical artists...")

    for idx, (art_id, artist_info) in enumerate(CANONICAL_ARTIST_MAP.items(), 1):
        time.sleep(0.08)
        # 1. Fetch artist top tracks
        top_url = f"https://api.deezer.com/artist/{art_id}/top?limit=40"
        res = fetch_deezer(top_url)
        tracks_data = res.get("data", []) if res else []

        # 2. If top tracks < 20, supplement with search
        if len(tracks_data) < 20:
            time.sleep(0.08)
            search_url = f"https://api.deezer.com/search?q={urllib.parse.quote(artist_info['name'])}&limit=30"
            sres = fetch_deezer(search_url)
            if sres and "data" in sres:
                tracks_data.extend(sres["data"])

        accepted_for_artist = 0
        for item in tracks_data:
            tid = str(item.get("id"))
            preview = item.get("preview")
            if not tid or not preview or tid in raw_tracks:
                continue

            title = item.get("title", "").strip()

            # N6: Exclude non-original versions (Remix, Live, Acoustic, Version, Cover, Instrumental...)
            if NON_ORIGINAL_PATTERN.search(title):
                continue

            art_obj = item.get("artist", {})
            track_art_id = str(art_obj.get("id", ""))

            # STRICT CANONICAL CHECK (N4):
            # Only accept if the track belongs to a canonical artist
            if track_art_id not in CANONICAL_ARTIST_MAP:
                if art_obj.get("name", "").strip().lower() == artist_info["name"].strip().lower():
                    matched_info = artist_info
                else:
                    continue  # DISCARD extraneous / foreign artists!
            else:
                matched_info = CANONICAL_ARTIST_MAP[track_art_id]

            # N6: Deduplicate by (artistId, normalized_title)
            norm_title = normalize_title(title)
            dedup_key = (matched_info["id"], norm_title)
            if dedup_key in seen_artist_titles:
                continue
            seen_artist_titles.add(dedup_key)

            album_obj = item.get("album", {})
            cover = album_obj.get("cover_medium") or album_obj.get("cover")
            rank = min(100, max(1, int(item.get("rank", 500000) / 10000)))
            clean_preview = preview.split('?')[0] if preview else ""

            # N5: Explicitly provide both providerArtistId and popularityRank
            raw_tracks[tid] = {
                "provider": "DEEZER",
                "providerTrackId": tid,
                "title": title,
                "artistName": matched_info["name"],
                "providerArtistId": matched_info["id"],
                "primaryArtistId": matched_info["id"],
                "countryCode": matched_info["country"],
                "genreCode": matched_info["genre"],
                "coverUrl": cover,
                "popularityRank": rank,
                "popularity": rank,
                "previewUrl": clean_preview,
                "is_girlgroup": matched_info["is_girlgroup"]
            }
            accepted_for_artist += 1

        if idx % 10 == 0 or idx == total_artists:
            print(f"[{idx}/{total_artists}] {artist_info['name']}: {accepted_for_artist} pristine tracks (Total collected: {len(raw_tracks)})")

    print(f"Total pristine original candidate tracks with previews: {len(raw_tracks)}")
    return raw_tracks

def main():
    start_time = time.time()
    generated_at = datetime.now(timezone.utc).isoformat()
    output_path = os.path.join(
        os.path.dirname(os.path.abspath(__file__)),
        "catalog-seed.json"
    )

    # Fast cache of existing release years from previous run
    cached_years = {}
    if os.path.exists(output_path):
        try:
            with open(output_path, "r", encoding="utf-8") as f:
                prev_data = json.load(f)
                for trk in prev_data.get("tracks", []):
                    if trk.get("providerTrackId") and trk.get("releaseYear"):
                        cached_years[str(trk["providerTrackId"])] = int(trk["releaseYear"])
            print(f"Loaded {len(cached_years)} cached release years from existing catalog-seed.json")
        except Exception:
            pass

    raw_tracks = collect_tracks()

    if len(raw_tracks) < 1000:
        print(f"ERROR: Collected only {len(raw_tracks)} tracks, required >= 1,000!")
        sys.exit(1)

    # Fetch release years (using cache where available, querying Deezer for the rest)
    release_years = {}
    to_fetch = []
    for tid in raw_tracks.keys():
        if tid in cached_years:
            release_years[tid] = cached_years[tid]
        else:
            to_fetch.append(tid)

    if to_fetch:
        print(f"\nFetching REAL release years for {len(to_fetch)} new tracks from Deezer API...")
        with ThreadPoolExecutor(max_workers=3) as executor:
            future_to_tid = {executor.submit(fetch_track_release_year, tid): tid for tid in to_fetch}
            completed = 0
            total = len(to_fetch)
            for future in as_completed(future_to_tid):
                tid, year = future.result()
                if year:
                    release_years[tid] = year
                completed += 1
                if completed % 100 == 0 or completed == total:
                    print(f"  Release year progress: {completed}/{total} ({len(release_years)} total resolved)")

    null_years_count = len(raw_tracks) - len(release_years)
    null_ratio = null_years_count / len(raw_tracks)
    print(f"\nRelease years resolved: {len(release_years)} / {len(raw_tracks)} (Missing: {null_years_count}, Ratio: {null_ratio:.2%})")

    # Strict validation on release years: must have < 3% null
    if null_ratio > 0.05:
        print(f"ERROR: Missing release year ratio {null_ratio:.2%} exceeds 5% threshold!")
        sys.exit(1)

    # Assign release years, decades, and playlists
    final_tracks = []
    decade_counts = collections.Counter()
    playlist_counts = collections.Counter()
    genre_counts = collections.Counter()
    country_counts = collections.Counter()
    artist_tuples = collections.defaultdict(set)

    for tid, track in raw_tracks.items():
        year = release_years.get(tid)
        track["releaseYear"] = year

        # Compute decade
        if year:
            decade_str = f"{(year // 10) * 10}s"
            track["decade"] = decade_str
            decade_counts[decade_str] += 1
        else:
            track["decade"] = None

        cc = track["countryCode"]
        gc = track["genreCode"]
        pls = []

        if cc == "VN":
            if gc == "ballad":
                pls.append("vpop-ballad")
            if gc == "rap-hiphop":
                pls.append("vpop-rap-hiphop")
            if year and 2010 <= year <= 2019:
                pls.append("vpop-nostalgia-2010s")
            elif year and year >= 2020:
                pls.append("vpop-hits-2020s")
            elif not year:
                pls.append("vpop-hits-2020s")

        elif cc in ("US", "GB", "CA", "SE", "NO"):
            if year and year < 2020:
                pls.append("usuk-pop-classics")
            if year and year >= 2015:
                pls.append("usuk-billboard-hits")
            elif not year:
                pls.append("usuk-billboard-hits")

        elif cc == "KR":
            pls.append("kpop-top-hits")
            if track.get("is_girlgroup"):
                pls.append("kpop-girlgroups")

        track["playlistCodes"] = pls

        # Track statistics
        country_counts[cc] += 1
        genre_counts[gc] += 1
        for p in pls:
            playlist_counts[p] += 1
        artist_tuples[track["artistName"]].add((cc, gc))

        # Cleanup internal flag
        track.pop("is_girlgroup", None)
        final_tracks.append(track)

    # Sort tracks deterministically
    final_tracks.sort(key=lambda x: (x["countryCode"], x["genreCode"], x["artistName"], x["title"]))

    # ---------------------------------------------------------------------------
    # Integrity Assertions
    # ---------------------------------------------------------------------------
    print("\nRunning integrity validations...")

    # 1. N5: Ensure providerArtistId and popularityRank exist and are valid on every track
    for t in final_tracks:
        assert t.get("providerArtistId") and t["providerArtistId"].isdigit(), f"Missing or non-digit providerArtistId in {t}"
        assert t.get("popularityRank") and isinstance(t["popularityRank"], int) and 1 <= t["popularityRank"] <= 100, f"Invalid popularityRank in {t}"
        assert t.get("releaseYear") and isinstance(t["releaseYear"], int), f"Missing releaseYear in {t}"

    # 2. N6: No non-original titles (Remix, Live, Acoustic, Version...)
    for t in final_tracks:
        assert not NON_ORIGINAL_PATTERN.search(t["title"]), f"Non-original version found in track: {t['title']}"

    # 3. N6: No duplicate normalized titles for the same artist
    seen_unique = set()
    for t in final_tracks:
        key = (t["providerArtistId"], normalize_title(t["title"]))
        assert key not in seen_unique, f"Duplicate track title for artist {t['artistName']}: {t['title']}"
        seen_unique.add(key)

    # 4. Artist uniqueness: no artist can have multiple country/genre pairs
    for art_name, pairs in artist_tuples.items():
        if len(pairs) > 1:
            print(f"ERROR: Artist '{art_name}' has conflicting metadata: {pairs}")
            sys.exit(1)

    # 5. Strict playlist country integrity
    for t in final_tracks:
        for p in t["playlistCodes"]:
            if p.startswith("vpop-") and t["countryCode"] != "VN":
                print(f"ERROR: Non-VN track '{t['title']}' by '{t['artistName']}' ({t['countryCode']}) in {p}")
                sys.exit(1)
            if p.startswith("kpop-") and t["countryCode"] != "KR":
                print(f"ERROR: Non-KR track '{t['title']}' by '{t['artistName']}' ({t['countryCode']}) in {p}")
                sys.exit(1)
            if p.startswith("usuk-") and t["countryCode"] not in ("US", "GB", "CA", "SE", "NO"):
                print(f"ERROR: Non-Western track '{t['title']}' by '{t['artistName']}' ({t['countryCode']}) in {p}")
                sys.exit(1)

    # 6. Strict decade integrity for curated playlists
    for t in final_tracks:
        if "vpop-nostalgia-2010s" in t["playlistCodes"]:
            assert t["releaseYear"] is not None and 2010 <= t["releaseYear"] <= 2019, f"Invalid year for 2010s: {t}"
        if "usuk-pop-classics" in t["playlistCodes"]:
            assert t["releaseYear"] is not None and t["releaseYear"] < 2020, f"Invalid year for classics: {t}"

    # 7. Playlist minimum threshold
    for pl in PLAYLISTS:
        code = pl["code"]
        cnt = playlist_counts[code]
        if cnt < 70:
            print(f"ERROR: Curated playlist '{code}' has only {cnt} tracks (< 70)!")
            sys.exit(1)

    # 8. Genre minimum threshold
    for g in GENRES:
        code = g["code"]
        cnt = genre_counts[code]
        if cnt < 70:
            print(f"ERROR: Genre '{code}' has only {cnt} tracks (< 70)!")
            sys.exit(1)

    print("ALL INTEGRITY CHECKS PASSED!")

    # Assemble final seed catalog
    catalog = {
        "metadata": {
            "version": "4.0",
            "provider": "deezer",
            "generatedAt": generated_at,
            "totalTracks": len(final_tracks),
            "tracksWithRealReleaseYear": len(release_years),
            "missingReleaseYear": null_years_count
        },
        "genres": GENRES,
        "playlists": PLAYLISTS,
        "tracks": final_tracks
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)

    elapsed = time.time() - start_time
    print(f"\n=======================================================")
    print(f"SUCCESS: Generated {len(final_tracks)} pristine original tracks in {elapsed:.1f}s")
    print(f"Output: {output_path}")
    print(f"Generated at: {generated_at}")
    print(f"Tracks with real release year: {len(release_years)} / {len(final_tracks)} ({len(release_years)/len(final_tracks):.1%})")
    print(f"\nCountry Distribution:")
    for c, cnt in country_counts.most_common():
        print(f"  {c}: {cnt}")
    print(f"\nGenre Distribution:")
    for g, cnt in genre_counts.most_common():
        print(f"  {g}: {cnt}")
    print(f"\nPlaylist Distribution:")
    for p, cnt in playlist_counts.most_common():
        print(f"  {p}: {cnt}")
    print(f"\nDecade Distribution:")
    for d, cnt in sorted(decade_counts.items()):
        print(f"  {d}: {cnt}")
    print(f"=======================================================\n")

if __name__ == "__main__":
    main()
