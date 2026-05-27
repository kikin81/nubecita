# Benchmark video fixtures

Three short H.264/AAC `.mp4` clips bundled into the `:app` benchmark flavor's
`assets/` so Macrobenchmark journeys (`FeedScrollBenchmark`,
`VideoPlaybackBenchmark`) can exercise Media3's progressive-MP4 path
deterministically without network access. See bd `nubecita-crmi.6`
Section E stage 1 for rationale.

Each clip is **~10–15 s, 720p, H.264 main profile, AAC stereo 128 kbps, MP4
container with faststart**, targeting **~2–3 MB on disk**. Three distinct
clips guarantee that six video posts in `timeline.json` force three unique
codec-init paths so the single-player coordinator's acquire/release/swap is
actually exercised under scroll.

These files are **not checked in via Git LFS** as long as the total bundled
video stays under the 20 MB threshold flagged in
`bd nubecita-crmi.6` (3 clips × ~3 MB ≈ 9 MB headroom). Cross that and
migrate to LFS.

## Clip roster

### `clip-1.mp4` — "scroll demo" (low motion, screen content)

- **Source.** Big Buck Bunny, opening title sequence (00:00:10–00:00:25).
- **Upstream URL.** <https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4>
  (use the 1080p source `BigBuckBunny_1080p.m4v` for higher quality input —
  link on the Blender Open Movies page <https://peach.blender.org/download/>).
- **License.** CC BY 3.0 — © 2008 Blender Foundation /
  [blender.org](https://www.blender.org). Attribution required (preserve
  this README).
- **Why this clip.** Mostly static title card transitioning into a low-motion
  opening pan. Exercises ExoPlayer's keyframe-and-hold pattern; low decode
  budget. Good control-arm clip.
- **Transcode.**

  ```bash
  ffmpeg -ss 10 -i BigBuckBunny_1080p.m4v -t 15 \
    -vf "scale=-2:720" \
    -c:v libx264 -profile:v main -level 4.0 -preset slow -crf 23 \
    -pix_fmt yuv420p \
    -c:a aac -b:a 128k -ac 2 \
    -movflags +faststart \
    clip-1.mp4
  ```

### `clip-2.mp4` — "roastery drum" (medium motion, continuous)

- **Source.** Sintel, scene 4 panning shot (pick any ~12 s segment from the
  10–30 s mark; the title cards are visually busy and motion-rich).
- **Upstream URL.** <https://download.blender.org/durian/movies/Sintel.2010.720p.mkv>
  (Blender Open Movies, Sintel feature page
  <https://durian.blender.org/download/>).
- **License.** CC BY 3.0 — © 2010 Blender Foundation /
  [durian.blender.org](https://durian.blender.org).
- **Why this clip.** Continuous motion across the frame, mid-range
  complexity. Exercises sustained decode + steady-state frame-pacing on the
  Surface — the realistic case for an inline-feed video.
- **Transcode.**

  ```bash
  ffmpeg -ss 12 -i Sintel.2010.720p.mkv -t 14 \
    -vf "scale=-2:720" \
    -c:v libx264 -profile:v main -level 4.0 -preset slow -crf 23 \
    -pix_fmt yuv420p \
    -c:a aac -b:a 128k -ac 2 \
    -movflags +faststart \
    clip-2.mp4
  ```

### `clip-3.mp4` — "scene cuts" (high motion, multi-cut)

- **Source.** Tears of Steel, opening sequence (00:00:30–00:00:45 — contains
  3–4 hard cuts, high motion, varied colour palettes).
- **Upstream URL.** <https://media.xiph.org/tearsofsteel/tearsofsteel-1080p.mp4>
  (Xiph mirror of the Mango Open Project, project page
  <https://mango.blender.org/download/>).
- **License.** CC BY 3.0 — © 2012 Blender Foundation /
  [mango.blender.org](https://mango.blender.org).
- **Why this clip.** Hard scene cuts force I-frame insertions and reset the
  decoder's prediction chain. Highest decode-budget clip; designed to be the
  worst case for sustained throughput. Combined with the other two, the
  three-clip set spans the codec's complexity range.
- **Transcode.**

  ```bash
  ffmpeg -ss 30 -i tearsofsteel-1080p.mp4 -t 15 \
    -vf "scale=-2:720" \
    -c:v libx264 -profile:v main -level 4.0 -preset slow -crf 23 \
    -pix_fmt yuv420p \
    -c:a aac -b:a 128k -ac 2 \
    -movflags +faststart \
    clip-3.mp4
  ```

## Why these specific clips

- **Codec coverage.** All three are H.264 main profile / AAC stereo —
  matching the most common Bluesky-served codec pair from the AppView's HLS
  ladder. We're not testing HEVC or AV1 in stage 1; those are reserved for a
  future tier when the asset HLS work in Section E stage 2 lands.
- **Motion complexity spectrum.** clip-1 (low) / clip-2 (medium) / clip-3
  (high) give the benchmark a defensible spread without bloating the APK.
  If a Compose / Media3 regression preferentially impacts high-motion decode
  (e.g. dropped P-frames under contention), clip-3 will surface it first.
- **Scene-cut count.** clip-3's hard cuts force keyframe insertion so the
  decoder cannot rely on motion-prediction caching — the single-player
  coordinator's acquire/release cost is more visible against a fresh
  decoder state than against a warmed one.
- **Provenance.** All three sources are CC BY 3.0 Blender Open Movies —
  one license type, three properly credited films, no commercial-use
  ambiguity. Attribution lives in this README; redistribution as part of
  the benchmark APK is permitted.

## Regenerating after upstream changes

The Blender Foundation hosts the master files indefinitely; if a URL
breaks, mirror by browsing the project page (linked above each clip). The
transcode commands above are pinned to specific timecodes so output is
byte-stable across re-runs given the same source and the same `ffmpeg`
build. Pin `ffmpeg` via a stable LTS (e.g. `ffmpeg 7.0`) when reproducibility
matters.

## Verification after transcode

```bash
# Confirm size, duration, and codec/profile
ffprobe -v error -select_streams v:0 -show_entries \
  stream=codec_name,profile,width,height,duration \
  -of default=noprint_wrappers=1 clip-1.mp4

# Confirm moov atom is at the front (faststart)
mp4dump --verbosity 1 clip-1.mp4 | head -20
```

Each clip should report `codec_name=h264`, `profile=Main`, `height=720`,
`duration≈10-15`, and the first listed atom should be `ftyp` followed
shortly by `moov` (not `mdat`).
