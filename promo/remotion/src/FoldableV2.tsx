import React from "react";
import {
  AbsoluteFill,
  Sequence,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { PALETTE, FRAUNCES, Layout, CtaOutro, Segment, SegmentedVideo, segmentsDuration } from "./shared";

const { NAVY, NAVY_2, ACCENT, WHITE } = PALETTE;

// Foldable v2: two clips from capture/journeys/foldable-v2.sh on the
// Nubecita_Bench_Fold AVD, whose inner display is nearly square (2076×2152).
//   open      portrait, flat: two panes, then Search with its second pane.
//   tabletop  landscape: the video player flat, then split on the fold.
// The drawn device turns a quarter between them (portrait and landscape slabs
// match once turned), and its bottom half folds back as the app's own layout
// switches to tabletop, so the bend and the split land together.
export type FoldV2Caption = { at: number; dur: number; lead: string; accent: string };

export type FoldableV2Journey = {
  openSrc: string;
  openSegments: Segment[];
  tabletopSrc: string;
  tabletopSegment: Segment; // one slice, played at rate 1 so the fold stays in sync
  tabletopSwitchSec: number; // source second the app's layout switches to tabletop
  openAspect: number; // width / height of the portrait capture
  captions: FoldV2Caption[];
};

const TURN_SEC = 0.9;
const FOLD_SEC = 0.7; // the bend, ending on the app's switch
const FOLD_DEG = 62; // the bottom half's tilt toward the viewer: flat enough to read as a stand, steep enough to show the controls
const CTA_SEC = 3.5;

export const foldableV2Timing = (j: FoldableV2Journey) => {
  const openEnd = segmentsDuration(j.openSegments);
  const tabletopStart = openEnd + TURN_SEC;
  const seg = j.tabletopSegment;
  const foldEnd = tabletopStart + (j.tabletopSwitchSec - seg.from);
  const outroStart = tabletopStart + (seg.to - seg.from);
  return { openEnd, tabletopStart, foldEnd, outroStart, durationSec: outroStart + CTA_SEC };
};

type Geo = {
  s: number; // the slab's long side; the short side is s × openAspect
  cx: number;
  cy: number;
  capX: number;
  capY: number;
  capW: number;
  capAlign: "center" | "left";
  capSize: number;
};

const geometry = (layout: Layout, w: number, h: number): Geo => {
  if (layout === "vertical") {
    return { s: w * 0.84, cx: w / 2, cy: h * 0.38, capX: w / 2, capY: h * 0.76, capW: w * 0.86, capAlign: "center", capSize: 76 };
  }
  if (layout === "square") {
    // Stacked like vertical: side by side leaves the caption too narrow.
    return { s: h * 0.6, cx: w / 2, cy: h * 0.4, capX: w / 2, capY: h * 0.8, capW: w * 0.9, capAlign: "center", capSize: 60 };
  }
  const s = h * 0.74;
  return { s, cx: w * 0.32, cy: h / 2, capX: w * 0.58 + (w * 0.38) / 2, capY: h * 0.4, capW: w * 0.38, capAlign: "left", capSize: 86 };
};

const BEZEL = 0.022; // × s
const RADIUS = 0.05; // × s

// A bezelled slab of the given size, screen clipped to its rounded inner rect.
const Slab: React.FC<{ w: number; h: number; s: number; crease: "vertical" | "none"; children: React.ReactNode }> = ({
  w,
  h,
  s,
  crease,
  children,
}) => {
  const bezel = s * BEZEL;
  const radius = s * RADIUS;
  return (
    <div
      style={{
        width: w,
        height: h,
        padding: bezel,
        boxSizing: "border-box",
        background: "#05070d",
        borderRadius: radius,
        boxShadow: "0 40px 120px rgba(0,0,0,0.55), 0 0 0 2px rgba(143,166,255,0.10)",
      }}
    >
      <div style={{ position: "relative", width: "100%", height: "100%", overflow: "hidden", borderRadius: radius - bezel, background: "#000" }}>
        {children}
        {crease === "vertical" ? (
          <div
            style={{
              position: "absolute",
              top: 0,
              bottom: 0,
              left: "50%",
              width: s * 0.012,
              transform: "translateX(-50%)",
              background: "linear-gradient(90deg, transparent, rgba(0,0,0,0.10), transparent)",
            }}
          />
        ) : null}
      </div>
    </div>
  );
};

// The landscape device in tabletop: the top half stays upright, the bottom half
// hinges on its top edge and tilts toward the viewer as if lying on the table.
// Each half shows its half of the same capture.
const TabletopDevice: React.FC<{ g: Geo; aspect: number; src: string; seg: Segment; fold: number }> = ({
  g,
  aspect,
  src,
  seg,
  fold,
}) => {
  const W = g.s;
  const H = g.s * aspect;
  const bezel = g.s * BEZEL;
  const radius = g.s * RADIUS;
  const innerW = W - 2 * bezel;
  const innerH = H - 2 * bezel;
  const half = (top: boolean): React.ReactNode => (
    <div
      style={{
        width: W,
        height: H / 2,
        boxSizing: "border-box",
        background: "#05070d",
        padding: `${top ? bezel : 0}px ${bezel}px ${top ? 0 : bezel}px`,
        borderRadius: top ? `${radius}px ${radius}px 0 0` : `0 0 ${radius}px ${radius}px`,
        transformOrigin: "top center",
        transform: top ? undefined : `rotateX(${fold * FOLD_DEG}deg)`,
        boxShadow: top ? undefined : `0 ${40 - 30 * fold}px 120px rgba(0,0,0,0.55)`,
      }}
    >
      <div
        style={{
          position: "relative",
          width: innerW,
          height: innerH / 2,
          overflow: "hidden",
          background: "#000",
          borderRadius: top ? `${radius - bezel}px ${radius - bezel}px 0 0` : `0 0 ${radius - bezel}px ${radius - bezel}px`,
        }}
      >
        <div style={{ position: "absolute", left: 0, top: top ? 0 : -innerH / 2, width: innerW, height: innerH }}>
          <SegmentedVideo src={src} segments={[seg]} />
        </div>
        {/* the hinge shades the half that's tilting away from the light */}
        {top ? null : (
          <div
            style={{
              position: "absolute",
              inset: 0,
              background: `linear-gradient(180deg, rgba(0,0,0,${0.45 * fold}) 0%, transparent 35%)`,
            }}
          />
        )}
      </div>
    </div>
  );
  return (
    <div style={{ width: W, height: H, perspective: g.s * 2.4, perspectiveOrigin: "50% 20%" }}>
      <div style={{ transformStyle: "preserve-3d" }}>
        {half(true)}
        {half(false)}
      </div>
    </div>
  );
};

const CaptionView: React.FC<{ g: Geo; lead: string; accent: string }> = ({ g, lead, accent }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const inP = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 18 });
  return (
    <div
      style={{
        position: "absolute",
        left: g.capX - g.capW / 2,
        top: g.capY,
        width: g.capW,
        transform: `translateY(${interpolate(inP, [0, 1], [28, 0])}px)`,
        opacity: inP,
        textAlign: g.capAlign,
        fontFamily: FRAUNCES,
        fontWeight: 600,
        fontSize: g.capSize,
        lineHeight: 1.05,
        color: WHITE,
        letterSpacing: -1,
      }}
    >
      {lead} <span style={{ color: ACCENT }}>{accent}</span>
    </div>
  );
};

export const FoldableV2: React.FC<{ layout: Layout; journey: FoldableV2Journey }> = ({ layout, journey }) => {
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();
  const g = geometry(layout, width, height);
  const t = foldableV2Timing(journey);
  const sec = frame / fps;

  const enter = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 24 });
  const turn = spring({
    frame: frame - t.openEnd * fps,
    fps,
    config: { damping: 200 },
    durationInFrames: Math.round(TURN_SEC * fps),
  });
  const fold = interpolate(sec, [t.foldEnd - FOLD_SEC, t.foldEnd], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  // Swap the turned portrait slab for the landscape one; they're the same size.
  const swap = interpolate(sec, [t.tabletopStart - 0.15, t.tabletopStart], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  const long = g.s;
  const short = g.s * journey.openAspect;
  const place = (w: number, h: number): React.CSSProperties => ({
    position: "absolute",
    left: g.cx - w / 2,
    top: g.cy - h / 2,
  });

  return (
    <AbsoluteFill style={{ background: `radial-gradient(120% 90% at 50% 20%, ${NAVY_2} 0%, ${NAVY} 62%)` }}>
      <div style={{ ...place(short, long), opacity: 1 - swap, transform: `scale(${interpolate(enter, [0, 1], [0.92, 1])}) rotate(${-90 * turn}deg)` }}>
        <Slab w={short} h={long} s={g.s} crease="vertical">
          <SegmentedVideo src={journey.openSrc} segments={journey.openSegments} />
        </Slab>
      </div>

      <Sequence from={Math.round(t.tabletopStart * fps)} layout="none">
        <div style={{ ...place(long, short) }}>
          <TabletopDevice g={g} aspect={journey.openAspect} src={journey.tabletopSrc} seg={journey.tabletopSegment} fold={fold} />
        </div>
      </Sequence>

      {journey.captions.map((c, i) => (
        <Sequence key={i} from={Math.round(c.at * fps)} durationInFrames={Math.round(c.dur * fps)}>
          <CaptionView g={g} lead={c.lead} accent={c.accent} />
        </Sequence>
      ))}
      <CtaOutro layout={layout} startSec={t.outroStart} />
    </AbsoluteFill>
  );
};
