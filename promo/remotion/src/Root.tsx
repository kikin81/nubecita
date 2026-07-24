import React from "react";
import { Composition } from "remotion";
import { Promo, Journey, Layout } from "./Promo";
import { Asset, ASSET_SPECS } from "./Assets";
import { Foldable, FoldableJourney } from "./Foldable";

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
    durationSec: 20,
    outroStartSec: 16.2,
    layouts: ["vertical", "square", "wide"],
    captions: [
      { at: 0.4, dur: 3.6, lead: "One tap to", accent: "like." },
      { at: 4.3, dur: 4.9, lead: "120 fps.", accent: "Zero jank." },
      { at: 9.5, dur: 5.7, lead: "Feed · Search · Chats ·", accent: "You." },
    ],
  },
  {
    id: "composer",
    device: "phone",
    videoSrc: "composer.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 14,
    outroStartSec: 11.5,
    layouts: ["vertical", "square"],
    captions: [
      { at: 0.6, dur: 3.3, lead: "Write your", accent: "post." },
      { at: 4.3, dur: 4.0, lead: "Say it with a", accent: "GIF." },
      { at: 8.6, dur: 3.2, lead: "One tap to", accent: "attach." },
    ],
  },
  {
    id: "threads",
    device: "phone",
    videoSrc: "threads.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 15,
    outroStartSec: 12.0,
    layouts: ["vertical", "square"],
    captions: [
      { at: 0.6, dur: 3.3, lead: "Follow the whole", accent: "thread." },
      { at: 4.3, dur: 4.1, lead: "Tap any photo to", accent: "zoom." },
      { at: 8.7, dur: 3.5, lead: "Every reply,", accent: "in place." },
    ],
  },
  {
    id: "search",
    device: "phone",
    videoSrc: "search.mp4",
    sourceAspect: PHONE_ASPECT,
    durationSec: 18,
    outroStartSec: 15.3,
    layouts: ["vertical", "square"],
    captions: [
      { at: 2.6, dur: 3.2, lead: "Find people to", accent: "follow." },
      { at: 6.0, dur: 2.9, lead: "Search", accent: "instantly." },
      { at: 9.3, dur: 5.6, lead: "Posts, people,", accent: "and feeds." },
    ],
  },
  {
    id: "tablet",
    device: "tablet",
    videoSrc: "tablet.mp4",
    sourceAspect: TABLET_ASPECT,
    durationSec: 15,
    outroStartSec: 12.8,
    layouts: ["wide", "square"],
    captions: [
      { at: 2.5, dur: 3.2, lead: "One app,", accent: "every screen." },
      { at: 6.0, dur: 2.9, lead: "Two panes,", accent: "more context." },
      { at: 9.3, dur: 3.4, lead: "Built for", accent: "tablets, too." },
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
