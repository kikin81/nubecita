import React from "react";
import { Composition } from "remotion";
import { Promo, Journey, Layout } from "./Promo";
import { Asset, ASSET_SPECS } from "./Assets";
import { Foldable, FoldableJourney } from "./Foldable";
import { Watch, WatchJourney } from "./Watch";
import { FoldableV2, FoldableV2Journey, foldableV2Timing } from "./FoldableV2";
import { segmentsDuration } from "./shared";

// Phone captures are 640x1428 (device 1280x2856); tablet captures are 1280x800
// (device 2560x1600). 60fps. Each journey's captured clip drives one story;
// captions are timed to the capture's beats and a shared CTA outro closes it.
const FPS = 60;
const PHONE_ASPECT = 640 / 1428;
const TABLET_ASPECT = 1280 / 800;

type JourneySpec = Journey & { durationSec: number; layouts: Layout[] };

const JOURNEYS: JourneySpec[] = [
  {
    id: "hero",
    device: "phone",
    videoSrc: "promo.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 20.5,
    outroStartSec: 17.0,
    layouts: ["vertical", "square", "wide"],
    captions: [
      { at: 0.4, dur: 4.0, lead: "One tap to", accent: "like." },
      { at: 4.7, dur: 5.5, lead: "120 fps.", accent: "Zero jank." },
      { at: 10.5, dur: 6.3, lead: "Feed · Search · Chats ·", accent: "You." },
    ],
  },
  {
    id: "composer",
    device: "phone",
    videoSrc: "composer.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 16.3,
    outroStartSec: 12.8,
    layouts: ["vertical", "square"],
    captions: [
      { at: 0.6, dur: 4.0, lead: "Write your", accent: "post." },
      { at: 4.9, dur: 3.5, lead: "Say it with a", accent: "GIF." },
      { at: 8.7, dur: 3.9, lead: "One tap to", accent: "attach." },
    ],
  },
  {
    id: "threads",
    device: "phone",
    videoSrc: "threads.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 19.3,
    outroStartSec: 15.8,
    layouts: ["vertical", "square"],
    captions: [
      { at: 0.5, dur: 3.7, lead: "Follow the whole", accent: "thread." },
      { at: 4.5, dur: 6.3, lead: "Tap any photo to", accent: "zoom." },
      { at: 11.0, dur: 4.6, lead: "Every reply,", accent: "in place." },
    ],
  },
  {
    id: "search",
    device: "phone",
    videoSrc: "search.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 22,
    outroStartSec: 18.5,
    layouts: ["vertical", "square"],
    captions: [
      { at: 1.2, dur: 4.6, lead: "See what's", accent: "trending." },
      { at: 6.1, dur: 3.0, lead: "Search", accent: "instantly." },
      { at: 9.4, dur: 8.9, lead: "Posts, feeds,", accent: "and starter packs." },
    ],
  },
  {
    id: "videos",
    device: "phone",
    videoSrc: "videos.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 19.1,
    outroStartSec: 15.6,
    layouts: ["vertical", "square"],
    captions: [
      { at: 0.4, dur: 2.9, lead: "Trending", accent: "videos." },
      { at: 3.6, dur: 2.6, lead: "Made for", accent: "vertical video." },
      { at: 6.4, dur: 2.4, lead: "Double-tap to", accent: "like." },
      { at: 9.0, dur: 2.8, lead: "Swipe for", accent: "the next one." },
      { at: 12.0, dur: 3.3, lead: "Tap to", accent: "pause." },
    ],
  },
  {
    id: "translation",
    device: "phone",
    videoSrc: "translation.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 18.5,
    outroStartSec: 15.0,
    layouts: ["vertical", "square"],
    captions: [
      { at: 0.4, dur: 3.0, lead: "Read posts in", accent: "any language." },
      { at: 3.7, dur: 3.9, lead: "Translate", accent: "automatically." },
      { at: 7.9, dur: 3.5, lead: "The original,", accent: "one tap away." },
      { at: 11.7, dur: 3.1, lead: "Replies,", accent: "translated too." },
    ],
  },
  {
    id: "tablet",
    device: "tablet",
    videoSrc: "tablet.mp4",
    sourceAspect: TABLET_ASPECT,
    durationSec: 20.3,
    outroStartSec: 16.8,
    layouts: ["wide", "square"],
    captions: [
      { at: 0.5, dur: 3.4, lead: "One app,", accent: "every screen." },
      { at: 4.1, dur: 3.7, lead: "Two panes,", accent: "more context." },
      { at: 8.1, dur: 8.5, lead: "Drag to resize,", accent: "it snaps." },
    ],
  },
];

const COVER_ASPECT = 1080 / 2092; // 0.516
const INNER_ASPECT = 2208 / 1840; // 1.20

type FoldableSpec = FoldableJourney & { id: string; durationSec: number; layouts: Layout[] };

const FOLDABLE: FoldableSpec = {
  id: "foldable",
  coverSrc: "fold_cover.mp4",
  innerSrc: "fold_inner.png",
  coverAspect: COVER_ASPECT,
  innerAspect: INNER_ASPECT,
  foldStartSec: 3.6,
  foldDurSec: 2.6,
  outroStartSec: 11.0,
  durationSec: 15,
  layouts: ["vertical", "wide"], // 9x16 + 16x9; add "square" for the 1x1 Ads cut
  captions: [
    { at: 0.4, dur: 3.0, lead: "Your whole timeline —", accent: "in your pocket." },
    { at: 6.7, dur: 3.7, lead: "Two panes,", accent: "more context." },
  ],
};

// Foldable v2: capture/journeys/foldable-v2.sh on the Nubecita_Bench_Fold AVD.
// Segments are source-clip seconds, matched to that script's beats.
type FoldableV2Spec = FoldableV2Journey & { id: string; layouts: Layout[] };

const FOLDABLE_V2: FoldableV2Spec = {
  id: "foldableV2",
  openSrc: "fold_open.mp4",
  openSegments: [
    { from: 2.2, to: 8.8, rate: 1.5 }, // tap a post → its thread in the second pane → read down
    { from: 8.8, to: 16.6, rate: 1.8 }, // Search: Explore in the second pane → type "coffee"
    { from: 16.6, to: 20.5, rate: 1 }, // results + the search summary pane
  ],
  tabletopSrc: "fold_tabletop.mp4",
  tabletopSegment: { from: 0.8, to: 9.5, rate: 1 }, // flat → hinge to 90° → split on the fold
  tabletopSwitchSec: 3.72, // first tabletop frame (223 @ 60 fps)
  openAspect: 1038 / 1076,
  layouts: ["vertical", "square", "wide"],
  captions: [
    { at: 0.3, dur: 4.0, lead: "Two panes,", accent: "more context." },
    { at: 4.6, dur: 3.9, lead: "Explore", accent: "side by side." },
    { at: 8.8, dur: 3.7, lead: "Every search,", accent: "summarized." },
    { at: 14.0, dur: 8.0, lead: "Fold it into", accent: "a stand." },
  ],
};

// Wear OS: capture/journeys/watch.sh records the round Wear_OS_Large_Round
// AVD (454×454) and resamples it to 60 fps; the Watch comp clips it to a
// circle. The segments are source-clip seconds, matched to that script's beats.
type WatchSpec = WatchJourney & { durationSec: number; layouts: Layout[] };

// Each section opens on the widget tap, then cuts past the app's launch
// splash straight to its content.
const WATCH_SEGMENTS = [
  { from: 0.4, to: 3.9, rate: 1 }, // watch face → launcher widget (2 new) → tap Home
  { from: 4.5, to: 17.2, rate: 1.8 }, // feed → post → repost (confirm) → bookmark
  { from: 19.2, to: 20.4, rate: 1 }, // widget → tap Messages
  { from: 21.0, to: 29.2, rate: 1.8 }, // Ana → Reply → "On my way" · Sent
  { from: 31.2, to: 32.3, rate: 1 }, // widget → tap New post
  { from: 32.8, to: 42.5, rate: 2.5 }, // keyboard → typing → Send
  { from: 43.1, to: 45.5, rate: 1.2 }, // past the keyboard closing: Post → "Posting…"
  { from: 54.2, to: 57.4, rate: 1 }, // posted card (the bench holds "Posting…" for 10 s)
];
const WATCH_CUT_SEC = segmentsDuration(WATCH_SEGMENTS); // ≈ 26.5 s

const WATCH: WatchSpec = {
  id: "watch",
  videoSrc: "watch.mp4",
  segments: WATCH_SEGMENTS,
  outroStartSec: WATCH_CUT_SEC,
  durationSec: WATCH_CUT_SEC + 3.5,
  layouts: ["vertical", "square", "wide"],
  captions: [
    { at: 0.3, dur: 3.0, lead: "Bluesky,", accent: "on your wrist." },
    { at: 3.7, dur: 6.7, lead: "Like and repost", accent: "in a tap." },
    { at: 10.8, dur: 5.3, lead: "Answer DMs", accent: "at a glance." },
    { at: 16.5, dur: 9.8, lead: "Post without", accent: "your phone." },
  ],
};

const DIMS: Record<Layout, { w: number; h: number; tag: string }> = {
  vertical: { w: 1080, h: 1920, tag: "9x16" },
  square: { w: 1080, h: 1080, tag: "1x1" },
  wide: { w: 1920, h: 1080, tag: "16x9" },
};

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export const RemotionRoot: React.FC = () => {
  return (
    <>
      {JOURNEYS.flatMap((j) =>
        j.layouts.map((layout) => {
          const d = DIMS[layout];
          return (
            <Composition
              key={`${j.id}-${d.tag}`}
              id={`${cap(j.id)}-${d.tag}`}
              component={Promo}
              durationInFrames={Math.round(j.durationSec * FPS)}
              fps={FPS}
              width={d.w}
              height={d.h}
              defaultProps={{ layout, journey: j }}
            />
          );
        }),
      )}

      {FOLDABLE.layouts.map((layout) => {
        const d = DIMS[layout];
        return (
          <Composition
            key={`${FOLDABLE.id}-${d.tag}`}
            id={`${cap(FOLDABLE.id)}-${d.tag}`}
            component={Foldable}
            durationInFrames={Math.round(FOLDABLE.durationSec * FPS)}
            fps={FPS}
            width={d.w}
            height={d.h}
            defaultProps={{ layout, journey: FOLDABLE }}
          />
        );
      })}

      {FOLDABLE_V2.layouts.map((layout) => {
        const d = DIMS[layout];
        return (
          <Composition
            key={`${FOLDABLE_V2.id}-${d.tag}`}
            id={`${cap(FOLDABLE_V2.id)}-${d.tag}`}
            component={FoldableV2}
            durationInFrames={Math.round(foldableV2Timing(FOLDABLE_V2).durationSec * FPS)}
            fps={FPS}
            width={d.w}
            height={d.h}
            defaultProps={{ layout, journey: FOLDABLE_V2 }}
          />
        );
      })}

      {WATCH.layouts.map((layout) => {
        const d = DIMS[layout];
        return (
          <Composition
            key={`${WATCH.id}-${d.tag}`}
            id={`${cap(WATCH.id)}-${d.tag}`}
            component={Watch}
            durationInFrames={Math.round(WATCH.durationSec * FPS)}
            fps={FPS}
            width={d.w}
            height={d.h}
            defaultProps={{ layout, journey: WATCH }}
          />
        );
      })}

      {/* Static image assets (rendered as stills) */}
      {ASSET_SPECS.map((spec) => (
        <Composition
          key={spec.id}
          id={spec.id}
          component={Asset}
          durationInFrames={1}
          fps={30}
          width={spec.width}
          height={spec.height}
          defaultProps={spec}
        />
      ))}
    </>
  );
};
