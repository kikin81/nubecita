# Foldable Unfold Promo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Remotion promo clip of a Pixel Fold scrolling the timeline on its cover display, then unfolding (3D CSS hinge) to reveal the list-detail two-pane on its inner display, with tight continuity (settle on post N → unfold reveals its thread).

**Architecture:** Extends the existing `promo/` pipeline (`capture/*.sh` → `remotion/src/*`). One new bench capture journey produces two clips (cover feed, inner two-pane); a new `Foldable.tsx` composition renders a CSS-3D hinge device that crossfades cover→inner as a right-half panel sweeps open; shared palette/fonts/CTA-outro are extracted into `shared.tsx`.

**Tech Stack:** Remotion 4.0.495 + React 19 (TSX), pure CSS 3D transforms, `adb`/`screenrecord` bench capture, `ffmpeg` raw→public encode.

## Global Constraints

Every task's requirements implicitly include these (copied from the spec):

- All work lives under `promo/`. **No shipped app module changes.** The only app touchpoint is *capturing* the bench build.
- **Bench flavor only** for captures (`:app:assembleBenchDebug` / `installBenchDebug`) — apolitical, offline, deterministic. Google Ads rejects real signed-in footage as political content.
- **Regenerable-from-source:** generated `.mp4`s and captured `.mp4` inputs stay **git-ignored** (existing `promo/.gitignore`). Commit source only (scripts + `.tsx`), never the clips.
- **No new npm dependencies** — Remotion 4.0.495 + CSS only.
- **Brand:** `PALETTE` = navy `#0A0E1A` / navy-2 `#111731` / accent periwinkle `#8FA6FF` / white `#F5F7FF`. Fraunces (captions + wordmark), Inter (tagline). Google Play badge (`public/gp_badge.png`) used **unmodified** (uniform scale only).
- **FPS = 60.**
- **Composition naming:** `Foldable-<tag>` where tag ∈ `9x16` (1080×1920) / `1x1` (1080×1080) / `16x9` (1920×1080), matching the existing `Foo-<tag>` scheme.
- **Fold display geometry:** cover `1080×2092` (aspect `0.516`), inner `2208×1840` (aspect `1.20`).
- **Capture device serial:** `37201FDHS002UN` (the plugged-in Pixel Fold).
- `adb shell input tap/swipe` coordinates are in the **`wm size` device space** (1080×2092 or 2208×1840), NOT the downscaled `screenrecord --size` output. (Same convention as `tablet.sh`.)

All commands below run from `promo/remotion/` unless noted (`npx remotion …`, `npx tsc …`). Capture commands run from `promo/`.

---

### Task 1: Extract `shared.tsx` (palette, fonts, `Layout`, `CtaOutro`)

Deduplicate the brand tokens and CTA outro out of `Promo.tsx` so `Foldable.tsx` reuses them. Regression gate: an existing Promo composition renders pixel-identically.

**Files:**
- Create: `promo/remotion/src/shared.tsx`
- Modify: `promo/remotion/src/Promo.tsx` (imports; delete local palette/font consts; replace inline CTA outro block)
- Modify: `promo/remotion/src/Root.tsx` (import `Layout` from `./shared` instead of `./Promo`)

**Interfaces:**
- Produces: `PALETTE: { NAVY; NAVY_2; ACCENT; WHITE }`, `FRAUNCES: string`, `INTER: string`, `type Layout = "vertical" | "square" | "wide"`, `CtaOutro: React.FC<{ layout: Layout; startSec: number }>`.
- Consumes: nothing (leaf module).

- [ ] **Step 1: Capture the regression baseline** (before any edit)

Render a still of the Tablet CTA-outro frame (Tablet uses `tablet.mp4`, which exists locally):

Run: `npx remotion still Tablet-16x9 ../out/base-outro.png --frame=810`
Expected: PNG written; it shows the "Nubecita / A fast, native Bluesky client / [Play badge]" outro.

- [ ] **Step 2: Create `shared.tsx`**

```tsx
import React from "react";
import {
  AbsoluteFill,
  Img,
  staticFile,
  interpolate,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { loadFont as loadFraunces } from "@remotion/google-fonts/Fraunces";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";

export type Layout = "vertical" | "square" | "wide";

const { fontFamily: FRAUNCES } = loadFraunces();
const { fontFamily: INTER } = loadInter();
export { FRAUNCES, INTER };

export const PALETTE = {
  NAVY: "#0A0E1A",
  NAVY_2: "#111731",
  ACCENT: "#8FA6FF",
  WHITE: "#F5F7FF",
} as const;

// The shared CTA closer: Nubecita wordmark + tagline + official Play badge,
// fading in over 0.5s from `startSec`. Owns its own opacity so callers just
// render it last in their AbsoluteFill stack.
export const CtaOutro: React.FC<{ layout: Layout; startSec: number }> = ({
  layout,
  startSec,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const start = startSec * fps;
  const opacity = interpolate(frame, [start, start + 0.5 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <AbsoluteFill
      style={{
        opacity,
        background: `radial-gradient(120% 90% at 50% 40%, ${PALETTE.NAVY_2} 0%, ${PALETTE.NAVY} 70%)`,
        alignItems: "center",
        justifyContent: "center",
        flexDirection: "column",
        gap: 28,
      }}
    >
      <div
        style={{
          fontFamily: FRAUNCES,
          fontWeight: 600,
          fontSize: layout === "wide" ? 150 : 128,
          color: PALETTE.WHITE,
          letterSpacing: -2,
        }}
      >
        Nubecita
      </div>
      <div
        style={{
          fontFamily: INTER,
          fontWeight: 500,
          fontSize: layout === "wide" ? 46 : 40,
          color: PALETTE.ACCENT,
          opacity: 0.95,
        }}
      >
        A fast, native Bluesky client
      </div>
      {/* Official Google Play badge — used unmodified (uniform scale only). */}
      <Img
        src={staticFile("gp_badge.png")}
        style={{ height: layout === "wide" ? 112 : 100, width: "auto", marginTop: 16 }}
      />
    </AbsoluteFill>
  );
};
```

- [ ] **Step 3: Rewire `Promo.tsx` to consume `shared.tsx`**

Replace the top import block + local consts. New imports at the top of `Promo.tsx`:

```tsx
import React from "react";
import {
  AbsoluteFill,
  OffthreadVideo,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
  interpolate,
  spring,
  Sequence,
} from "remotion";
import { PALETTE, FRAUNCES, INTER, Layout, CtaOutro } from "./shared";

const { NAVY, NAVY_2, ACCENT, WHITE } = PALETTE;
```

Then in `Promo.tsx`:
- **Delete** the two `loadFont` imports, the `const { fontFamily: FRAUNCES } = …` / `INTER` lines, the four `const NAVY … WHITE` lines, and the local `export type Layout = …` line (now imported).
- **Delete** the `Img` import usage for the badge and the entire CTA-outro `AbsoluteFill` block at the bottom of the `Promo` component, plus the now-unused `outroStart` / `outro` locals.
- **Replace** that deleted outro block with a single line as the last child of the root `AbsoluteFill`:

```tsx
      <CtaOutro layout={layout} startSec={journey.outroStartSec} />
```

(`INTER` is re-exported for any downstream use; `FRAUNCES`/`ACCENT`/`WHITE`/`NAVY*` remain referenced by `CaptionView`/`geometry`/background.)

- [ ] **Step 4: Point `Root.tsx` at the shared `Layout`**

In `Root.tsx`, change `import { Promo, Journey, Layout } from "./Promo";` to:

```tsx
import { Promo, Journey } from "./Promo";
import { Layout } from "./shared";
```

(`Promo.tsx` should re-export `Layout` too, OR keep `Journey` importing it — simplest: `Promo.tsx` adds `export type { Layout } from "./shared";` so existing `Journey` references resolve. Add that re-export line to `Promo.tsx`.)

- [ ] **Step 5: Typecheck**

Run: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 6: Render the regression still and compare**

Run:
```bash
npx remotion still Tablet-16x9 ../out/after-outro.png --frame=810
```
Then compare bytes/pixels:
```bash
cmp ../out/base-outro.png ../out/after-outro.png && echo "IDENTICAL" || echo "DIFF — inspect both PNGs"
```
Expected: `IDENTICAL` (the refactor is behavior-preserving). If DIFF, open both — any visible change means the outro was altered; reconcile before committing.

- [ ] **Step 7: Commit**

```bash
git add promo/remotion/src/shared.tsx promo/remotion/src/Promo.tsx promo/remotion/src/Root.tsx
git commit -m "refactor(promo): extract shared palette, fonts, and CTA outro into shared.tsx

Refs: nubecita-i6zk"
```

---

### Task 2: Capture journey `foldable.sh` → `fold_cover.mp4` + `fold_inner.mp4`

Deterministic bench capture of the two displays via `wm size`. Device-dependent; the operator runs it on the plugged-in Fold.

**Files:**
- Create: `promo/capture/journeys/foldable.sh`
- Produces (git-ignored, into `promo/remotion/public/`): `fold_cover.mp4`, `fold_inner.mp4`

**Interfaces:**
- Produces: two clips consumed by `Foldable.tsx` as `staticFile("fold_cover.mp4")` / `staticFile("fold_inner.mp4")`, at aspects `0.516` and `1.20`, each settled on **post N = the 3rd feed card**.

- [ ] **Step 1: Ensure the bench build is installed on the Fold**

Run (from repo root):
```bash
./gradlew :app:installBenchDebug -Pandroid.injected.testOnly=false
adb -s 37201FDHS002UN shell settings put global stay_on_while_plugged_in 7
```
Expected: `Installed on 1 device` (disconnect any TCP re-pair first: `adb disconnect $(adb devices | grep _adb-tls-connect | awk '{print $1}')`).

- [ ] **Step 2: Write `foldable.sh`**

```bash
#!/usr/bin/env bash
# Journey: Foldable unfold. Two segments captured at the Pixel Fold's two display
# geometries via `wm size` (reproducible — same trick as tablet.sh):
#   1) COVER  1080x2092 @ density 420 -> compact single-pane feed, settle on post N
#   2) INNER  2208x1840 @ density 380 -> expanded list-detail two-pane, tap post N
# Post N = the 3rd feed card. Outputs fold_cover_raw.mp4 + fold_inner_raw.mp4.
# Bench flavor, all fake/apolitical data. Restores display + demo mode on exit.
# NOTE: input tap/swipe coords are in the wm-size DEVICE space, not the record size.
set -euo pipefail
SERIAL="${1:-37201FDHS002UN}"; PKG="net.kikin.nubecita"
A(){ adb -s "$SERIAL" "$@"; }; SH(){ A shell "$@"; }
tap(){ SH input tap "$1" "$2"; }
drag(){ SH input swipe "$1" "$2" "$3" "$4" "${5:-700}"; }
pause(){ sleep "$1"; }

cleanup(){
  SH pkill -INT screenrecord >/dev/null 2>&1 || true
  SH wm size reset >/dev/null 2>&1 || true
  SH wm density reset >/dev/null 2>&1 || true
  SH settings put global heads_up_notifications_enabled 1 >/dev/null 2>&1 || true
  SH am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
}
trap cleanup EXIT

demo(){
  SH settings put global sysui_demo_allowed 1 || true
  SH am broadcast -a com.android.systemui.demo -e command enter >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null || true
  SH am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null || true
}

launch(){ SH am force-stop "$PKG"; pause 0.5; SH am start -n "$PKG/.MainActivity" >/dev/null; pause 6; }

echo "[dnd] suppress heads-up notifications (avoid stolen taps)"
SH settings put global heads_up_notifications_enabled 0 || true

# ---------- Segment 1: COVER (single-pane feed) ----------
echo "[cover] resize 1080x2092 @ 420 -> compact"
SH wm size 1080x2092; SH wm density 420; pause 1
demo; launch
echo "[cover] record (downscaled 540x1046)"
SH screenrecord --size 540x1046 --bit-rate 12000000 --time-limit 20 /sdcard/fold_cover.mp4 &
RID=$!; pause 1.2
# settle so the 3rd card (post N) rests mid-screen; two gentle scrolls
drag 540 1500 540 1040; pause 1.6
drag 540 1400 540 1120; pause 2.4
SH pkill -INT screenrecord >/dev/null 2>&1 || true; wait "$RID" 2>/dev/null || true; pause 1
A pull /sdcard/fold_cover.mp4 fold_cover_raw.mp4 >/dev/null

# ---------- Segment 2: INNER (two-pane list-detail) ----------
echo "[inner] resize 2208x1840 @ 380 -> expanded two-pane"
SH wm size 2208x1840; SH wm density 380; pause 1
demo; launch
echo "[inner] record (downscaled 1104x920)"
SH screenrecord --size 1104x920 --bit-rate 16000000 --time-limit 20 /sdcard/fold_inner.mp4 &
RID=$!; pause 1.2
# scroll the LEFT list pane to post N, then tap it -> right pane shows its thread
drag 460 1300 460 900; pause 1.4        # list pane ≈ left 40% of 2208 -> x≈460
tap 460 640; pause 2.8                   # tap post N in the list -> detail pane fills
SH pkill -INT screenrecord >/dev/null 2>&1 || true; wait "$RID" 2>/dev/null || true; pause 1
A pull /sdcard/fold_inner.mp4 fold_inner_raw.mp4 >/dev/null

echo "done: fold_cover_raw.mp4  fold_inner_raw.mp4"
```

- [ ] **Step 3: Make it executable and run it**

Run (from `promo/`):
```bash
chmod +x capture/journeys/foldable.sh
capture/journeys/foldable.sh 37201FDHS002UN
```
Expected: `done: fold_cover_raw.mp4 fold_inner_raw.mp4` and both files present in `promo/`.

- [ ] **Step 4: Verify each clip settles on post N (visual)**

Extract a late frame from each and eyeball that the cover feed and the inner **left** pane show the *same* 3rd card, and the inner **right** pane shows that post's thread:
```bash
ffmpeg -y -sseof -2 -i fold_cover_raw.mp4 -frames:v 1 ../out/chk_cover.png
ffmpeg -y -sseof -2 -i fold_inner_raw.mp4 -frames:v 1 ../out/chk_inner.png
```
Expected: `chk_cover.png` = single-column feed resting on post N; `chk_inner.png` = two-pane, left list on post N, right pane its thread. If the inner is single-pane (not two-pane), **lower the density** (e.g. `340`) so 2208px reads as a wider dp → expanded, and re-run. If taps missed, adjust `tap`/`drag` coords (device space) and re-run.

- [ ] **Step 5: Encode into `remotion/public/` (yuv420p h264 for OffthreadVideo)**

```bash
ffmpeg -y -i fold_cover_raw.mp4 -c:v libx264 -pix_fmt yuv420p -an remotion/public/fold_cover.mp4
ffmpeg -y -i fold_inner_raw.mp4 -c:v libx264 -pix_fmt yuv420p -an remotion/public/fold_inner.mp4
```
Expected: both written under `promo/remotion/public/` (git-ignored).

- [ ] **Step 6: Commit the script only** (clips are git-ignored)

```bash
git add promo/capture/journeys/foldable.sh
git commit -m "feat(promo): bench capture journey for the foldable unfold (cover + inner two-pane)

Refs: nubecita-i6zk"
```

---

### Task 3: `Foldable.tsx` static open state + `Root.tsx` registration

Render the flat, open two-pane device + captions + CTA outro (NO hinge yet). This isolates geometry/captions/outro from the hard 3D work.

**Files:**
- Create: `promo/remotion/src/Foldable.tsx`
- Modify: `promo/remotion/src/Root.tsx` (register `Foldable-9x16` + `Foldable-16x9`)

**Interfaces:**
- Consumes (Task 1): `PALETTE`, `FRAUNCES`, `Layout`, `CtaOutro` from `./shared`.
- Consumes (Task 2): `public/fold_cover.mp4`, `public/fold_inner.mp4`.
- Produces: `Foldable: React.FC<{ layout: Layout; journey: FoldableJourney }>`; `type FoldableJourney` with fields `{ coverSrc; innerSrc; coverAspect; innerAspect; foldStartSec; foldDurSec; outroStartSec; captions: FoldCaption[] }`; `type FoldCaption = { at; dur; lead; accent }`.

- [ ] **Step 1: Create `Foldable.tsx` (open state)**

```tsx
import React from "react";
import {
  AbsoluteFill,
  OffthreadVideo,
  Sequence,
  staticFile,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { PALETTE, FRAUNCES, Layout, CtaOutro } from "./shared";

export type FoldCaption = { at: number; dur: number; lead: string; accent: string };

export type FoldableJourney = {
  coverSrc: string; // fold_cover.mp4  (aspect ~0.516)
  innerSrc: string; // fold_inner.mp4  (aspect ~1.20)
  coverAspect: number;
  innerAspect: number;
  foldStartSec: number; // unfold begins (used in Task 4)
  foldDurSec: number; // unfold duration (used in Task 4)
  outroStartSec: number;
  captions: FoldCaption[];
};

// Fit the OPEN inner display within the frame (device sits in the upper 2/3).
export const fitInner = (innerAspect: number, w: number, h: number) => {
  const maxW = w * 0.9;
  const maxH = h * 0.6;
  let dw = maxW;
  let dh = dw / innerAspect;
  if (dh > maxH) {
    dh = maxH;
    dw = dh * innerAspect;
  }
  return { dw, dh };
};

const CaptionView: React.FC<{ lead: string; accent: string; w: number; y: number }> = ({
  lead,
  accent,
  w,
  y,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const inP = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 18 });
  const dy = interpolate(inP, [0, 1], [28, 0]);
  return (
    <div
      style={{
        position: "absolute",
        left: w * 0.07,
        top: y,
        width: w * 0.86,
        transform: `translateY(${dy}px)`,
        opacity: inP,
        textAlign: "center",
        fontFamily: FRAUNCES,
        fontWeight: 600,
        fontSize: 72,
        lineHeight: 1.05,
        color: PALETTE.WHITE,
        letterSpacing: -1,
      }}
    >
      {lead} <span style={{ color: PALETTE.ACCENT }}>{accent}</span>
    </div>
  );
};

// Flat/open inner two-pane display. The two halves render the left/right
// portions of fold_inner.mp4 (Task 4 splits them onto the hinge).
const InnerDisplay: React.FC<{ src: string; dw: number; dh: number }> = ({ src, dw, dh }) => {
  const bezel = dw * 0.014;
  const radius = dw * 0.045;
  const Half: React.FC<{ side: "left" | "right" }> = ({ side }) => (
    <div style={{ width: dw / 2, height: dh, overflow: "hidden", position: "relative" }}>
      <OffthreadVideo
        src={staticFile(src)}
        style={{
          position: "absolute",
          top: 0,
          left: side === "left" ? 0 : -dw / 2,
          width: dw,
          height: dh,
          objectFit: "cover",
        }}
      />
    </div>
  );
  return (
    <div
      style={{
        width: dw,
        height: dh,
        borderRadius: radius,
        background: "#05070d",
        padding: bezel,
        boxShadow: "0 40px 120px rgba(0,0,0,0.55), 0 0 0 2px rgba(143,166,255,0.10)",
        boxSizing: "border-box",
      }}
    >
      <div
        style={{
          display: "flex",
          width: "100%",
          height: "100%",
          borderRadius: radius - bezel,
          overflow: "hidden",
          background: "#000",
        }}
      >
        <Half side="left" />
        <Half side="right" />
      </div>
    </div>
  );
};

export const Foldable: React.FC<{ layout: Layout; journey: FoldableJourney }> = ({
  layout,
  journey,
}) => {
  const { width, height, fps } = useVideoConfig();
  const { dw, dh } = fitInner(journey.innerAspect, width, height);
  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(120% 90% at 50% 12%, ${PALETTE.NAVY_2} 0%, ${PALETTE.NAVY} 62%)`,
      }}
    >
      <div style={{ position: "absolute", left: width / 2 - dw / 2, top: height * 0.4 - dh / 2 }}>
        <InnerDisplay src={journey.innerSrc} dw={dw} dh={dh} />
      </div>

      {journey.captions.map((c, i) => (
        <Sequence
          key={i}
          from={Math.round(c.at * fps)}
          durationInFrames={Math.round(c.dur * fps)}
        >
          <CaptionView lead={c.lead} accent={c.accent} w={width} y={height * 0.86} />
        </Sequence>
      ))}

      <CtaOutro layout={layout} startSec={journey.outroStartSec} />
    </AbsoluteFill>
  );
};
```

- [ ] **Step 2: Register the compositions in `Root.tsx`**

Add near the other imports:
```tsx
import { Foldable, FoldableJourney } from "./Foldable";
```
Add above `RemotionRoot` (after the `JOURNEYS` array):
```tsx
const COVER_ASPECT = 1080 / 2092; // 0.516
const INNER_ASPECT = 2208 / 1840; // 1.20

type FoldableSpec = FoldableJourney & { id: string; durationSec: number; layouts: Layout[] };

const FOLDABLE: FoldableSpec = {
  id: "foldable",
  coverSrc: "fold_cover.mp4",
  innerSrc: "fold_inner.mp4",
  coverAspect: COVER_ASPECT,
  innerAspect: INNER_ASPECT,
  foldStartSec: 4.0,
  foldDurSec: 3.0,
  outroStartSec: 11.0,
  durationSec: 15,
  layouts: ["vertical", "wide"], // 9x16 + 16x9; add "square" for the 1x1 Ads cut
  captions: [
    { at: 0.4, dur: 3.4, lead: "Your whole timeline —", accent: "in your pocket." },
    { at: 7.2, dur: 3.4, lead: "Two panes,", accent: "more context." },
  ],
};
```
Add inside the `RemotionRoot` fragment, alongside the existing `JOURNEYS.flatMap(...)` block:
```tsx
      {FOLDABLE.layouts.map((layout) => {
        const d = DIMS[layout];
        return (
          <Composition
            key={`foldable-${d.tag}`}
            id={`Foldable-${d.tag}`}
            component={Foldable}
            durationInFrames={Math.round(FOLDABLE.durationSec * FPS)}
            fps={FPS}
            width={d.w}
            height={d.h}
            defaultProps={{ layout, journey: FOLDABLE }}
          />
        );
      })}
```

- [ ] **Step 3: Typecheck**

Run: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 4: Render an open-state still**

Run: `npx remotion still Foldable-9x16 ../out/foldable-open.png --frame=540`
Expected (frame 540 = 9.0s, in the open window): the bezelled two-pane device centered upper-2/3 (left list pane on post N, right its thread), the "Two panes, more context." caption below, on the navy gradient. Inspect the PNG.

- [ ] **Step 5: Commit**

```bash
git add promo/remotion/src/Foldable.tsx promo/remotion/src/Root.tsx
git commit -m "feat(promo): Foldable open two-pane composition + registration (no hinge yet)

Refs: nubecita-i6zk"
```

---

### Task 4: 3D CSS hinge unfold

Replace the static `InnerDisplay` with a `FoldingDevice` that shows the folded cover, then sweeps the right half open on the spine and crossfades cover→inner. **This is the tune-in-Studio task** — the code below is a working first pass; the deliverable is a hinge that *reads as a real Pixel Fold*.

**Files:**
- Modify: `promo/remotion/src/Foldable.tsx` (replace `InnerDisplay` with `FoldingDevice`; drive `unfold` progress)

**Interfaces:**
- Consumes: `FoldableJourney` (uses `coverSrc`, `foldStartSec`, `foldDurSec` now), `fitInner`.
- Produces: no new exports (internal `FoldingDevice`).

- [ ] **Step 1: Add `FoldingDevice` and wire the unfold progress**

In `Foldable.tsx`, **delete** the `InnerDisplay` component and **replace** it with:

```tsx
const PERSPECTIVE = 2600; // tune in Studio
const HINGE_DAMP = 200;

const FoldingDevice: React.FC<{
  journey: FoldableJourney;
  dw: number; // open inner width
  dh: number; // open inner height
  unfold: number; // 0 = folded (cover), 1 = flat open (two-pane)
}> = ({ journey, dw, dh, unfold }) => {
  const bezel = dw * 0.014;
  const radius = dw * 0.045;
  const halfW = dw / 2;

  const rightRot = interpolate(unfold, [0, 1], [-178, 0]); // deg, swings on the spine
  const coverOpacity = interpolate(unfold, [0, 0.45], [1, 0], { extrapolateRight: "clamp" });
  const innerOpacity = interpolate(unfold, [0.3, 0.62], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const glare = interpolate(unfold, [0.15, 0.85], [-1.1, 1.1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const VideoHalf: React.FC<{ side: "left" | "right" }> = ({ side }) => (
    <div style={{ position: "absolute", inset: 0, overflow: "hidden", borderRadius: radius - bezel }}>
      <OffthreadVideo
        src={staticFile(journey.innerSrc)}
        style={{
          position: "absolute",
          top: 0,
          left: side === "left" ? 0 : -(halfW - bezel),
          width: dw - bezel * 2,
          height: dh - bezel * 2,
          objectFit: "cover",
        }}
      />
    </div>
  );

  const Panel: React.FC<{ children: React.ReactNode; style?: React.CSSProperties }> = ({
    children,
    style,
  }) => (
    <div style={{ width: halfW, height: dh, background: "#05070d", padding: bezel, boxSizing: "border-box", ...style }}>
      <div style={{ position: "relative", width: "100%", height: "100%", background: "#000", borderRadius: radius - bezel, overflow: "hidden" }}>
        {children}
      </div>
    </div>
  );

  return (
    <div style={{ position: "relative", width: dw, height: dh, perspective: PERSPECTIVE }}>
      {/* inner two-pane — fades in as it opens; right half rides the hinge */}
      <div style={{ position: "absolute", inset: 0, display: "flex", opacity: innerOpacity, transformStyle: "preserve-3d" }}>
        <Panel>
          <VideoHalf side="left" />
        </Panel>
        <Panel
          style={{
            transformOrigin: "left center",
            transform: `rotateY(${rightRot}deg)`,
            backfaceVisibility: "hidden",
          }}
        >
          <VideoHalf side="right" />
        </Panel>
      </div>

      {/* folded cover panel — occupies the left-half footprint, fades out on open */}
      <div style={{ position: "absolute", left: 0, top: 0, width: halfW, height: dh, opacity: coverOpacity }}>
        <div style={{ width: "100%", height: "100%", background: "#05070d", padding: bezel, boxSizing: "border-box", borderRadius: radius, boxShadow: "0 40px 120px rgba(0,0,0,0.55)" }}>
          <div style={{ width: "100%", height: "100%", background: "#000", borderRadius: radius - bezel, overflow: "hidden" }}>
            <OffthreadVideo src={staticFile(journey.coverSrc)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
          </div>
        </div>
      </div>

      {/* glass catch-light sweep */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          pointerEvents: "none",
          background: "linear-gradient(105deg, transparent 0%, rgba(255,255,255,0.12) 50%, transparent 100%)",
          transform: `translateX(${glare * dw}px)`,
          mixBlendMode: "screen",
          opacity: innerOpacity,
        }}
      />
    </div>
  );
};
```

Then in the `Foldable` component, compute `unfold` and swap the device element:

```tsx
  const frame = useCurrentFrame();
  const start = journey.foldStartSec * fps;
  const dur = journey.foldDurSec * fps;
  const unfold = spring({
    frame: frame - start,
    fps,
    config: { damping: HINGE_DAMP },
    durationInFrames: dur,
  });
```
Replace the `<div ...><InnerDisplay .../></div>` block with:
```tsx
      <div style={{ position: "absolute", left: width / 2 - dw / 2, top: height * 0.4 - dh / 2 }}>
        <FoldingDevice journey={journey} dw={dw} dh={dh} unfold={unfold} />
      </div>
```
(Add `useCurrentFrame` to the import if not already present — it is.)

- [ ] **Step 2: Typecheck**

Run: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Tune the hinge in Studio (the real work — visual checkpoint)**

Run: `npx remotion studio`
Open `Foldable-9x16`, scrub 0→15s. Verify the arc:
- 0–4s: only the **folded cover** feed is visible (tall-ish left panel), no seam/second panel showing.
- 4–7s: right half **swings out** on the spine (reads as depth, not a flat wipe); cover **crossfades** into the two-pane; lands flat with no gap at the spine.
- 7–11s: clean flat two-pane; then the CTA outro.

Tune these constants until it reads as a real Fold (this is expected iteration, not a failure): `PERSPECTIVE`, the `rightRot` range `[-178, 0]`, the `coverOpacity`/`innerOpacity` crossfade thresholds, `HINGE_DAMP`, and the cover-panel size/position (the folded cover may want a taller portrait footprint via `journey.coverAspect` rather than `dh` — adjust the cover `<div>` height to `halfW / journey.coverAspect` and re-center if the closed slab looks too squat). Commit the tuned constants.

- [ ] **Step 4: Render the full 9x16 to confirm no dropped frames**

Run: `npx remotion render Foldable-9x16 ../out/foldable-9x16.mp4`
Expected: completes without error; open the mp4 and confirm the unfold plays smoothly.

- [ ] **Step 5: Commit**

```bash
git add promo/remotion/src/Foldable.tsx
git commit -m "feat(promo): 3D CSS hinge unfold — folded cover sweeps open to the two-pane

Refs: nubecita-i6zk"
```

---

### Task 5: Aspect variants + final renders

Confirm 16:9 (and optionally 1:1) fit correctly and produce the deliverable renders.

**Files:**
- Modify: `promo/remotion/src/Foldable.tsx` (only if `fitInner` needs per-aspect tuning for `wide`)
- Modify: `promo/remotion/src/Root.tsx` (only if adding `Foldable-1x1`)

**Interfaces:**
- Consumes: everything from Tasks 3–4. No new exports.

- [ ] **Step 1: Render the 16:9 still and check fit**

Run: `npx remotion still Foldable-16x9 ../out/foldable-16x9-open.png --frame=540`
Expected: the open near-square device fits within the wide frame without clipping, caption legible. If the device is too tall/short for `wide`, adjust the `maxH`/`maxW` factors in `fitInner` (e.g. gate on a `layout`/aspect argument) — keep `9x16` unchanged (re-render `Foldable-9x16` still to confirm no regression).

- [ ] **Step 2: (Optional) add the 1:1 Ads cut**

Only if the full Ads trifecta is wanted: change `FOLDABLE.layouts` in `Root.tsx` to `["vertical", "wide", "square"]`, typecheck, and render a `Foldable-1x1` still to confirm fit.

- [ ] **Step 3: Render both deliverables**

Run:
```bash
npx remotion render Foldable-9x16 ../out/foldable-9x16.mp4
npx remotion render Foldable-16x9 ../out/foldable-16x9.mp4
```
Expected: both complete cleanly. These are the deliverables (git-ignored under `promo/out/`).

- [ ] **Step 4: Commit any fit/registration tweaks**

```bash
git add promo/remotion/src/Foldable.tsx promo/remotion/src/Root.tsx
git commit -m "feat(promo): foldable aspect fit (16x9) + final render targets

Refs: nubecita-i6zk"
```

---

## Self-Review

**Spec coverage:**
- 3D CSS hinge unfold → Task 4. ✅
- Real bench footage on the Fold, both displays → Task 2 (`wm size` refinement noted below). ✅
- Tight continuity (post N cover → inner detail) → Task 2 Steps 2/4. ✅
- Design-once/export-two-cuts (9x16 + 16x9, optional 1x1) → Tasks 3 & 5. ✅
- `shared.tsx` refactor (palette/fonts/CtaOutro dedup) → Task 1. ✅
- Beats & captions (folded / unfold / open / outro) → Task 3 (`FOLDABLE.captions`, `foldStartSec`). ✅
- Outputs git-ignored, source-only commits → Tasks 2/4/5 (commit scripts + tsx, never mp4s). ✅
- Validation = visual/still/render (no unit tests) → each task's render/still step. ✅

**Deliberate spec deviation (flagged):** the spec described a *semi-manual physical fold/unfold* capture; the plan uses the `wm size`+density trick (Task 2) instead — deterministic, reproducible, no manual step, and consistent with the existing `tablet.sh`. Same authentic per-display geometry. Raise before executing if physical-display capture is specifically wanted.

**Placeholder scan:** no TBD/TODO; every code step has complete code; the one genuinely iterative step (Task 4 Step 3, hinge tuning) ships working code + a concrete visual checklist, which is the honest shape of that work.

**Type consistency:** `FoldableJourney`/`FoldCaption`/`fitInner`/`Foldable` names match across Tasks 3–5; `Layout`/`PALETTE`/`CtaOutro` names match Task 1's exports; `DIMS`/`FPS`/`Composition` reuse the existing `Root.tsx` symbols.

**Open (non-blocking, resolve in-flight):** exact settle index/coords for post N (Task 2 Step 4 adjusts on-device); whether the hero cut wants a longer open dwell; whether to ship `Foldable-1x1` (Task 5 Step 2).
