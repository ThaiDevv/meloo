#!/usr/bin/env python3
"""
Fetch real tracks from Deezer API to generate backend/src/main/resources/seed/catalog-seed.json.
Guarantees:
- Real Deezer numeric track IDs (e.g. "2470200565")
- Real Deezer numeric artist IDs (e.g. "5400939") with unified artist mapping
- Real static CDN album cover URLs
- Real working preview URLs (without ephemeral signed tokens)
- Real release years fetched directly from Deezer /track/{id} API (no synthetic years!)
- Real decade grouping: vpop-nostalgia-2010s strictly contains 2010-2019 hits, usuk-pop-classics strictly contains pre-2020 classics
- Balanced distribution across V-Pop (VN), US-UK (US, GB, CA), and K-Pop (KR)
- Non-empty tracks for all 8 Curated Playlists and all 8 Genres
- Strictly >= 1,000 valid tracks
"""

import collections
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import os
import sys
import time
import urllib.parse
import urllib.request

VPOP_SOURCES = [
    # Rap & Hip-hop (ensures vpop-rap-hiphop is well populated)
    ("Den Vau", "VN", "rap-hiphop"),
    ("HIEUTHUHAI", "VN", "rap-hiphop"),
    ("Binz", "VN", "rap-hiphop"),
    ("Karik", "VN", "rap-hiphop"),
    ("tlinh", "VN", "rap-hiphop"),
    ("B Ray", "VN", "rap-hiphop"),
    ("Rhymastic", "VN", "rap-hiphop"),
    # 2010s Nostalgia Ballads & Hits
    ("My Tam", "VN", "ballad"),
    ("Ha Anh Tuan", "VN", "ballad"),
    ("Bui Anh Tuan", "VN", "ballad"),
    ("Huong Tram", "VN", "ballad"),
    ("Khac Viet", "VN", "ballad"),
    ("Phan Manh Quynh", "VN", "ballad"),
    ("Dong Nhi", "VN", "vpop"),
    ("Noo Phuoc Thinh", "VN", "vpop"),
    ("Trung Quan Idol", "VN", "ballad"),
    # 2020s Hits & Modern Pop / R&B
    ("Son Tung M-TP", "VN", "vpop"),
    ("Mono", "VN", "vpop"),
    ("Tang Duy Tan", "VN", "vpop"),
    ("Hoang Thuy Linh", "VN", "vpop"),
    ("Vu", "VN", "ballad"),
    ("Bich Phuong", "VN", "vpop"),
    ("Soobin Hoang Son", "VN", "rnb"),
    ("JustaTee", "VN", "rnb"),
    ("Hoa Minzy", "VN", "vpop"),
    ("Duc Phuc", "VN", "ballad"),
    ("Erik", "VN", "ballad"),
    ("Amee", "VN", "vpop"),
    ("Wren Evans", "VN", "vpop"),
    ("Grey D", "VN", "vpop"),
    ("Min", "VN", "vpop"),
    ("Jack J97", "VN", "vpop"),
    ("Van Mai Huong", "VN", "ballad")
]

USUK_SOURCES = [
    # EDM
    ("Avicii", "SE", "edm"),
    ("Calvin Harris", "GB", "edm"),
    ("The Chainsmokers", "US", "edm"),
    ("Alan Walker", "NO", "edm"),
    # Rock / Classics
    ("Queen", "GB", "rock"),
    ("Coldplay", "GB", "rock"),
    ("Imagine Dragons", "US", "rock"),
    # R&B
    ("The Weeknd", "CA", "rnb"),
    ("Bruno Mars", "US", "rnb"),
    ("Beyonce", "US", "rnb"),
    # Rap & Hip-hop
    ("Eminem", "US", "rap-hiphop"),
    ("Post Malone", "US", "rap-hiphop"),
    # Pop & Ballad
    ("Taylor Swift", "US", "usuk-pop"),
    ("Ed Sheeran", "GB", "usuk-pop"),
    ("Ariana Grande", "US", "usuk-pop"),
    ("Adele", "GB", "ballad"),
    ("Dua Lipa", "GB", "usuk-pop"),
    ("Billie Eilish", "US", "usuk-pop"),
    ("Justin Bieber", "CA", "usuk-pop"),
    ("Maroon 5", "US", "usuk-pop"),
    ("Katy Perry", "US", "usuk-pop"),
    ("Charlie Puth", "US", "usuk-pop")
]

KPOP_SOURCES = [
    ("BTS", "KR", "kpop", False),
    ("Blackpink", "KR", "kpop", True),
    ("NewJeans", "KR", "kpop", True),
    ("IVE", "KR", "kpop", True),
    ("TWICE", "KR", "kpop", True),
    ("aespa", "KR", "kpop", True),
    ("LE SSERAFIM", "KR", "kpop", True),
    ("IU", "KR", "kpop", True),
    ("BIGBANG", "KR", "kpop", False),
    ("EXO", "KR", "kpop", False),
    ("Stray Kids", "KR", "kpop", False),
    ("SEVENTEEN", "KR", "kpop", False),
    ("NCT 127", "KR", "kpop", False),
    ("Red Velvet", "KR", "kpop", True),
    ("(G)I-DLE", "KR", "kpop", True),
    ("ITZY", "KR", "kpop", True),
    ("TXT", "KR", "kpop", False),
    ("ENHYPEN", "KR", "kpop", False)
]

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

def fetch_deezer(url):
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "MelooCatalogSeeder/2.0 (Academic Project; tranvanthai20062@gmail.com)",
            "Accept": "application/json"
        }
    )
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = resp.read().decode("utf-8")
            return json.loads(data)
    except Exception as e:
        return None

def fetch_track_release_year(tid):
    time.sleep(0.08)  # Throttle for rate limiting (~12 req/sec across workers)
    url = f"https://api.deezer.com/track/{tid}"
    data = fetch_deezer(url)
    if data:
        rd = data.get("release_date") or data.get("album", {}).get("release_date")
        if rd and isinstance(rd, str) and len(rd) >= 4:
            year_str = rd.split("-")[0]
            if year_str.isdigit() and (year_str.startswith("19") or year_str.startswith("20")):
                return tid, int(year_str)
    return tid, None

def collect_tracks(sources, per_source_limit, raw_tracks, canonical_artist):
    for item_tuple in sources:
        query = item_tuple[0]
        country = item_tuple[1]
        genre = item_tuple[2]
        is_girlgroup = item_tuple[3] if len(item_tuple) > 3 else False

        encoded = urllib.parse.quote(query)
        url = f"https://api.deezer.com/search?q={encoded}&limit={per_source_limit}"
        res = fetch_deezer(url)
        if not res or "data" not in res:
            time.sleep(0.1)
            continue

        for item in res["data"]:
            tid = str(item.get("id"))
            preview = item.get("preview")
            if not tid or not preview or tid in raw_tracks:
                continue

            art_obj = item.get("artist", {})
            art_id = str(art_obj.get("id", ""))
            art_name = art_obj.get("name", "Unknown")

            # Canonical artist mapping (m3: ensure artist has consistent country & genre)
            if art_id not in canonical_artist:
                canonical_artist[art_id] = {
                    "name": art_name,
                    "country": country,
                    "genre": genre
                }

            artist_info = canonical_artist[art_id]
            album_obj = item.get("album", {})
            cover = album_obj.get("cover_medium") or album_obj.get("cover")
            rank = min(100, max(1, int(item.get("rank", 500000) / 10000)))
            clean_preview = preview.split('?')[0] if preview else ""

            raw_tracks[tid] = {
                "tid": tid,
                "title": item.get("title", ""),
                "artistName": artist_info["name"],
                "providerArtistId": art_id,
                "countryCode": artist_info["country"],
                "genreCode": artist_info["genre"],
                "coverUrl": cover,
                "previewUrl": clean_preview,
                "popularityRank": rank,
                "isGirlgroup": is_girlgroup
            }

        time.sleep(0.1)

def main():
    raw_tracks = {}
    canonical_artist = {}

    print("=== Step 1: Collecting V-Pop tracks ===")
    collect_tracks(VPOP_SOURCES, 16, raw_tracks, canonical_artist)
    print(f"V-Pop collected: {sum(1 for t in raw_tracks.values() if t['countryCode'] == 'VN')} tracks\n")

    print("=== Step 2: Collecting US-UK tracks ===")
    collect_tracks(USUK_SOURCES, 22, raw_tracks, canonical_artist)
    print(f"US-UK collected: {sum(1 for t in raw_tracks.values() if t['countryCode'] in ('US', 'GB', 'CA', 'SE', 'NO'))} tracks\n")

    print("=== Step 3: Collecting K-Pop tracks ===")
    collect_tracks(KPOP_SOURCES, 22, raw_tracks, canonical_artist)
    print(f"K-Pop collected: {sum(1 for t in raw_tracks.values() if t['countryCode'] == 'KR')} tracks\n")

    print(f"Total candidate tracks: {len(raw_tracks)}")
    if len(raw_tracks) < 1000:
        raise ValueError(f"Too few tracks collected: {len(raw_tracks)} < 1000")

    print("\n=== Step 4: Fetching Real Release Dates from Deezer API ===")
    tids = list(raw_tracks.keys())
    release_years = {}
    done_count = 0
    start_time = time.time()

    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = {executor.submit(fetch_track_release_year, tid): tid for tid in tids}
        for future in as_completed(futures):
            tid, year = future.result()
            if year:
                release_years[tid] = year
            done_count += 1
            if done_count % 100 == 0 or done_count == len(tids):
                print(f"Fetched release dates: {done_count}/{len(tids)} ({time.time() - start_time:.1f}s)")

    print(f"Successfully retrieved real release years for {len(release_years)}/{len(tids)} tracks.")

    # Assign Playlists based on Real Release Years & Genres
    final_tracks = []
    for tid, t in raw_tracks.items():
        year = release_years.get(tid)
        playlists = set()
        c_code = t["countryCode"]
        g_code = t["genreCode"]

        if c_code == "VN":
            if year and 2010 <= year <= 2019:
                playlists.add("vpop-nostalgia-2010s")
            elif year and year >= 2020:
                playlists.add("vpop-hits-2020s")
            else:
                playlists.add("vpop-hits-2020s")

            if g_code == "ballad":
                playlists.add("vpop-ballad")
            elif g_code == "rap-hiphop":
                playlists.add("vpop-rap-hiphop")

        elif c_code in ("US", "GB", "CA", "SE", "NO"):
            if year and year < 2020:
                playlists.add("usuk-pop-classics")
            else:
                playlists.add("usuk-billboard-hits")

        elif c_code == "KR":
            playlists.add("kpop-top-hits")
            if t.get("isGirlgroup"):
                playlists.add("kpop-girlgroups")

        final_tracks.append({
            "provider": "DEEZER",
            "providerTrackId": tid,
            "title": t["title"],
            "artistName": t["artistName"],
            "providerArtistId": t["providerArtistId"],
            "genreCode": g_code,
            "countryCode": c_code,
            "releaseYear": year,
            "popularityRank": t["popularityRank"],
            "previewUrl": t["previewUrl"],
            "coverUrl": t["coverUrl"],
            "playlistCodes": sorted(list(playlists))
        })

    # Sort deterministically
    final_tracks.sort(key=lambda x: int(x["providerTrackId"]))

    # Validation Checks
    country_counts = collections.Counter(t["countryCode"] for t in final_tracks)
    print("\nCountry distribution:", dict(country_counts))
    if country_counts.get("VN", 0) < 300:
        raise ValueError(f"Too few VN tracks: {country_counts.get('VN', 0)}")
    if country_counts.get("US", 0) < 150:
        raise ValueError(f"Too few US tracks: {country_counts.get('US', 0)}")
    if country_counts.get("GB", 0) < 40:
        raise ValueError(f"Too few GB tracks: {country_counts.get('GB', 0)}")
    if country_counts.get("KR", 0) < 200:
        raise ValueError(f"Too few KR tracks: {country_counts.get('KR', 0)}")

    playlist_counts = collections.Counter(
        p for t in final_tracks for p in (t.get("playlistCodes") or [])
    )
    print("Playlist distribution:", dict(playlist_counts))
    for p in PLAYLISTS:
        cnt = playlist_counts.get(p["code"], 0)
        if cnt < 20:
            raise ValueError(f"Playlist '{p['code']}' has only {cnt} tracks (minimum 20 required)")

    genre_counts = collections.Counter(t["genreCode"] for t in final_tracks)
    print("Genre distribution:", dict(genre_counts))
    for g in GENRES:
        cnt = genre_counts.get(g["code"], 0)
        if cnt < 20:
            raise ValueError(f"Genre '{g['code']}' has only {cnt} tracks (minimum 20 required)")

    # Validate AC1 & N3: Decade checks
    nostalgia_2010s = [
        t for t in final_tracks
        if "vpop-nostalgia-2010s" in t["playlistCodes"] and t["releaseYear"] and 2010 <= t["releaseYear"] <= 2019
    ]
    print(f"Real 2010s V-Pop tracks in vpop-nostalgia-2010s: {len(nostalgia_2010s)}")
    if len(nostalgia_2010s) < 20:
        raise ValueError(f"vpop-nostalgia-2010s has only {len(nostalgia_2010s)} real 2010s tracks")

    usuk_classics = [
        t for t in final_tracks
        if "usuk-pop-classics" in t["playlistCodes"] and t["releaseYear"] and t["releaseYear"] < 2020
    ]
    print(f"Real pre-2020 US-UK classics in usuk-pop-classics: {len(usuk_classics)}")
    if len(usuk_classics) < 20:
        raise ValueError(f"usuk-pop-classics has only {len(usuk_classics)} real pre-2020 tracks")

    catalog = {
        "genres": GENRES,
        "playlists": PLAYLISTS,
        "tracks": final_tracks
    }

    out_file = "backend/src/main/resources/seed/catalog-seed.json"
    os.makedirs(os.path.dirname(out_file), exist_ok=True)
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)

    print(f"\nSUCCESS: Generated {out_file} with {len(final_tracks)} tracks having REAL release years.")

if __name__ == "__main__":
    main()
