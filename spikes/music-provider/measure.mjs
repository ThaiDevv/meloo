#!/usr/bin/env node

import { createHash } from "node:crypto";
import { execFile } from "node:child_process";
import { readFile, writeFile } from "node:fs/promises";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const DEEZER_SEARCH = "https://api.deezer.com/search";
const ITUNES_SEARCH = "https://itunes.apple.com/search";
const args = new Map(process.argv.slice(2).map((arg) => arg.split("=", 2)));
const inputPath = args.get("--input") ?? "tracks.csv";
const outputPath = args.get("--output") ?? "results.csv";
const summaryPath = args.get("--summary") ?? "summary.json";
const delayMs = Number(args.get("--delay-ms") ?? 3000);
const probeConcurrency = Number(args.get("--probe-concurrency") ?? 6);
const onlyTrackId = args.get("--only") ?? "";

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (char === '"') {
      if (quoted && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === "," && !quoted) {
      row.push(field);
      field = "";
    } else if ((char === "\n" || char === "\r") && !quoted) {
      if (char === "\r" && text[index + 1] === "\n") index += 1;
      row.push(field);
      if (row.some(Boolean)) rows.push(row);
      row = [];
      field = "";
    } else {
      field += char;
    }
  }
  if (field || row.length) {
    row.push(field);
    rows.push(row);
  }
  const [headers, ...values] = rows;
  return values.map((value) => Object.fromEntries(headers.map((header, index) => [header, value[index] ?? ""])));
}

function csvCell(value) {
  const text = value === null || value === undefined ? "" : String(value);
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toCsv(rows) {
  const headers = [...new Set(rows.flatMap((row) => Object.keys(row)))];
  return `${headers.join(",")}\n${rows.map((row) => headers.map((header) => csvCell(row[header])).join(",")).join("\n")}\n`;
}

function normalize(value) {
  return value
    .normalize("NFD")
    .replaceAll(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .replaceAll(/\b(feat|ft|with)\b.*$/gu, "")
    .replaceAll(/[^a-z0-9]+/gu, " ")
    .trim();
}

function tokenScore(expected, actual) {
  const left = new Set(normalize(expected).split(" ").filter(Boolean));
  const right = new Set(normalize(actual).split(" ").filter(Boolean));
  const intersection = [...left].filter((token) => right.has(token)).length;
  const union = new Set([...left, ...right]).size;
  return union === 0 ? 0 : intersection / union;
}

function artistScore(expected, actual) {
  const expectedTokens = new Set(normalize(expected).split(" ").filter(Boolean));
  const actualTokens = new Set(normalize(actual).split(" ").filter(Boolean));
  if ([...expectedTokens].every((token) => actualTokens.has(token))) return 1;
  return tokenScore(expected, actual);
}

function matchScore(track, candidate) {
  const titleScore = tokenScore(track.title, candidate.title);
  const creditedArtistScore = artistScore(track.artist, candidate.artist);
  return {
    titleScore: Number(titleScore.toFixed(3)),
    artistScore: Number(creditedArtistScore.toFixed(3)),
    matchScore: Number((titleScore * 0.7 + creditedArtistScore * 0.3).toFixed(3)),
  };
}

async function fetchJson(url) {
  const response = await fetch(url, { headers: { "User-Agent": "Meloo-MEL-25-Spike/1.0" }, signal: AbortSignal.timeout(20_000) });
  if (!response.ok) throw new Error(`HTTP ${response.status} ${response.statusText}`);
  const payload = await response.json();
  if (payload.error) {
    const { code, message, type } = payload.error;
    throw new Error(`API error${code ? ` ${code}` : ""}${type ? ` (${type})` : ""}: ${message ?? "unknown error"}`);
  }
  return payload;
}

function selectBest(track, candidates) {
  return candidates
    .map((candidate) => ({ ...candidate, ...matchScore(track, candidate) }))
    .sort((left, right) => right.matchScore - left.matchScore)[0] ?? null;
}

function isAccepted(result) {
  return result && result.matchScore >= 0.7 && result.titleScore >= 0.5 && result.artistScore >= 0.5;
}

async function searchDeezer(track) {
  const url = new URL(DEEZER_SEARCH);
  url.searchParams.set("q", `${track.title} ${track.artist}`);
  url.searchParams.set("limit", "10");
  const payload = await fetchJson(url);
  return selectBest(track, (payload.data ?? []).map((item) => ({
    id: item.id,
    title: item.title_short ?? item.title,
    artist: item.artist?.name ?? "",
    previewUrl: item.preview ?? "",
    catalogUrl: item.link ?? "",
  })));
}

async function searchItunes(track) {
  const url = new URL(ITUNES_SEARCH);
  url.searchParams.set("term", `${track.title} ${track.artist}`);
  url.searchParams.set("country", track.storefront);
  url.searchParams.set("media", "music");
  url.searchParams.set("entity", "song");
  url.searchParams.set("limit", "10");
  const payload = await fetchJson(url);
  return selectBest(track, (payload.results ?? []).map((item) => ({
    id: item.trackId,
    title: item.trackName ?? "",
    artist: item.artistName ?? "",
    previewUrl: item.previewUrl ?? "",
    catalogUrl: item.trackViewUrl ?? "",
  })));
}

function expiryEvidence(previewUrl) {
  if (!previewUrl) return { urlExpiry: "no-preview", urlExpiryAt: "" };
  try {
    const url = new URL(previewUrl);
    const signedPolicy = url.searchParams.get("hdnea") ?? "";
    const epoch = signedPolicy.match(/(?:^|~)exp=(\d+)/)?.[1];
    if (epoch) return { urlExpiry: "signed-expiry", urlExpiryAt: new Date(Number(epoch) * 1000).toISOString() };
    return { urlExpiry: "no-expiry-marker", urlExpiryAt: "" };
  } catch {
    return { urlExpiry: "invalid-url", urlExpiryAt: "" };
  }
}

function variantSuffix(title) {
  const marker = title.match(/\b(?:japanese|korean|chinese)\s+version\b|\b(?:tropical|acoustic|live|remix|instrumental|karaoke|cover|demo|sped\s*up|slowed\s*down)\b/i);
  return marker?.[0] ?? "";
}

async function probe(previewUrl) {
  if (!previewUrl) return {};
  try {
    const { stdout } = await execFileAsync("ffprobe", [
      "-v", "error",
      "-show_entries", "format=format_name,duration,start_time,bit_rate:stream=codec_name,codec_type,bit_rate",
      "-of", "json",
      previewUrl,
    ], { timeout: 30_000, maxBuffer: 1024 * 1024 });
    const parsed = JSON.parse(stdout);
    const audio = parsed.streams?.find((stream) => stream.codec_type === "audio") ?? {};
    return {
      format: parsed.format?.format_name ?? audio.codec_name ?? "",
      codec: audio.codec_name ?? "",
      bitrateKbps: Math.round(Number(audio.bit_rate ?? parsed.format?.bit_rate ?? 0) / 1000) || "",
      previewDurationSeconds: Number(Number(parsed.format?.duration ?? 0).toFixed(3)) || "",
      containerStartSeconds: Number(Number(parsed.format?.start_time ?? 0).toFixed(3)),
    };
  } catch (error) {
    return { probeError: error.message.replaceAll(/\s+/g, " ").slice(0, 240) };
  }
}

async function mapConcurrent(items, concurrency, mapper) {
  const results = new Array(items.length);
  let cursor = 0;
  async function worker() {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await mapper(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
  return results;
}

function aggregate(rows) {
  const summary = {};
  for (const row of rows) {
    const key = `${row.group}/${row.provider}`;
    summary[key] ??= { group: row.group, provider: row.provider, tracks: 0, matched: 0, previews: 0, originalRecordings: 0, variants: 0, probeSuccess: 0 };
    summary[key].tracks += 1;
    summary[key].matched += row.matched === "yes" ? 1 : 0;
    summary[key].previews += row.previewAvailable === "yes" ? 1 : 0;
    summary[key].originalRecordings += row.previewAvailable === "yes" && row.originalRecording === "yes" ? 1 : 0;
    summary[key].variants += row.previewAvailable === "yes" && row.variantSuffix ? 1 : 0;
    summary[key].probeSuccess += row.previewAvailable === "yes" && !row.probeError ? 1 : 0;
  }
  return Object.values(summary).map((item) => ({
    ...item,
    previewRatePercent: Number(((item.previews / item.tracks) * 100).toFixed(1)),
  }));
}

const tracks = parseCsv(await readFile(inputPath, "utf8"));
if (tracks.length !== 70) throw new Error(`Expected 70 tracks, received ${tracks.length}`);
const groupCounts = Object.groupBy(tracks, (track) => track.group);
const expectedCounts = { "V-pop": 30, "K-pop": 20, "US-UK": 20 };
for (const [group, expected] of Object.entries(expectedCounts)) {
  if ((groupCounts[group]?.length ?? 0) !== expected) throw new Error(`${group}: expected ${expected} tracks`);
}
const selectedTracks = onlyTrackId ? tracks.filter((track) => track.id === onlyTrackId) : tracks;
if (selectedTracks.length === 0) throw new Error(`Track not found: ${onlyTrackId}`);

const searches = [];
for (const [index, track] of selectedTracks.entries()) {
  process.stdout.write(`\rSearch ${index + 1}/${selectedTracks.length}: ${track.id} ${track.title}                              `);
  const measuredAt = new Date().toISOString();
  const [deezer, itunes] = await Promise.allSettled([searchDeezer(track), searchItunes(track)]);
  searches.push(
    { track, provider: "Deezer", measuredAt, result: deezer.status === "fulfilled" ? deezer.value : null, searchError: deezer.status === "rejected" ? deezer.reason.message : "" },
    { track, provider: "iTunes", measuredAt, result: itunes.status === "fulfilled" ? itunes.value : null, searchError: itunes.status === "rejected" ? itunes.reason.message : "" },
  );
  if (index < selectedTracks.length - 1) await new Promise((resolve) => setTimeout(resolve, delayMs));
}
process.stdout.write("\n");

const measured = await mapConcurrent(searches, probeConcurrency, async (entry, index) => {
  process.stdout.write(`\rProbe ${index + 1}/${searches.length}                                              `);
  const accepted = isAccepted(entry.result) ? entry.result : null;
  const previewUrl = accepted?.previewUrl ?? "";
  const technical = await probe(previewUrl);
  return {
    trackId: entry.track.id,
    group: entry.track.group,
    expectedTitle: entry.track.title,
    expectedArtist: entry.track.artist,
    storefront: entry.track.storefront,
    provider: entry.provider,
    measuredAt: entry.measuredAt,
    matched: accepted ? "yes" : "no",
    matchScore: entry.result?.matchScore ?? "",
    providerTrackId: entry.result?.id ?? "",
    returnedTitle: entry.result?.title ?? "",
    returnedArtist: entry.result?.artist ?? "",
    variantSuffix: accepted ? variantSuffix(entry.result.title) : "",
    originalRecording: accepted && !variantSuffix(entry.result.title) ? "yes" : "no",
    previewAvailable: previewUrl ? "yes" : "no",
    ...technical,
    musicalExcerptStart: "not-exposed-by-provider",
    ...expiryEvidence(previewUrl),
    previewUrlSha256: previewUrl ? createHash("sha256").update(previewUrl).digest("hex") : "",
    catalogUrl: entry.result?.catalogUrl ?? "",
    searchError: entry.searchError,
    probeError: technical.probeError ?? "",
  };
});
process.stdout.write("\n");

let allRows = measured;
if (onlyTrackId) {
  const existingRows = parseCsv(await readFile(outputPath, "utf8"));
  const order = new Map(tracks.flatMap((track, index) => [[`${track.id}/Deezer`, index * 2], [`${track.id}/iTunes`, index * 2 + 1]]));
  allRows = [...existingRows.filter((row) => row.trackId !== onlyTrackId), ...measured]
    .sort((left, right) => order.get(`${left.trackId}/${left.provider}`) - order.get(`${right.trackId}/${right.provider}`));
}
await writeFile(outputPath, toCsv(allRows), "utf8");
await writeFile(summaryPath, `${JSON.stringify({
  generatedAt: new Date().toISOString(),
  methodology: {
    matchThreshold: 0.7,
    itunesStorefront: "VN",
    musicalExcerptStart: "Providers do not expose the offset within the full recording; container start is measured separately.",
    previewUrlsStored: false,
  },
  groups: aggregate(allRows),
}, null, 2)}\n`, "utf8");
process.stdout.write(`Wrote ${outputPath} and ${summaryPath}\n`);
