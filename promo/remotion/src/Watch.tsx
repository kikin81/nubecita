import React from "react";
import {
  AbsoluteFill,
  useCurrentFrame,
  useVideoConfig,
  interpolate,
  spring,
  Sequence,
} from "remotion";
import { PALETTE, FRAUNCES, Layout, CtaOutro, Segment, SegmentedVideo } from "./shared";

const { NAVY, NAVY_2, ACCENT, WHITE } = PALETTE;

// A Wear OS journey. The capture is the square screenrecord of a round
// emulator (Wear_OS_Large_Round = 454×454); the composition clips it to a
// circle inside a drawn watch case, so the source needs no masking.
export type WatchCaption = { at: number; dur: number; lead: string; accent: string };

export type WatchJourney = {
  id: string;
  videoSrc: string; // square capture in public/, constant 60 fps
  segments: Segment[]; // the capture runs ~57 s; the cut speeds up and skips dead time
  outroStartSec: number;
  captions: WatchCaption[];
};

type Geo = {
  d: number; // watch screen diameter
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
    const d = w * 0.62;
    return {
      d,
      cx: w / 2,
      cy: h * 0.4,
      capX: w / 2,
      capY: h * 0.76,
      capW: w * 0.86,
      capAlign: "center",
      capSize: 76,
    };
  }
  // Side-by-side: the watch case is ~1.23× the screen diameter, so size the
  // screen off the case and start the caption clear of the crown.
  if (layout === "square") {
    const d = h * 0.44;
    return {
      d,
      cx: w * 0.3,
      cy: h / 2,
      capX: w * 0.6 + (w * 0.36) / 2,
      capY: h * 0.42,
      capW: w * 0.36,
      capAlign: "left",
      capSize: 62,
    };
  }
  const d = h * 0.52;
  return {
    d,
    cx: w * 0.28,
    cy: h / 2,
    capX: w * 0.52 + (w * 0.42) / 2,
    capY: h * 0.4,
    capW: w * 0.42,
    capAlign: "left",
    capSize: 88,
  };
};

// Round case: brushed-metal ring, a thin black bezel, the crown on the right
// and two strap stubs. Proportions are relative to the screen diameter `d`.
const WatchDevice: React.FC<{ g: Geo; src: string; segments: Segment[] }> = ({
  g,
  src,
  segments,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const enter = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 24 });
  const scale = interpolate(enter, [0, 1], [0.92, 1]);

  const { d } = g;
  const bezel = d * 0.045;
  const ring = d * 0.07;
  const caseD = d + 2 * (bezel + ring);
  const strapW = d * 0.62;
  const strapH = caseD * 0.42;

  const strap = (top: boolean): React.CSSProperties => ({
    position: "absolute",
    left: (caseD - strapW) / 2,
    width: strapW,
    height: strapH,
    [top ? "top" : "bottom"]: -strapH * 0.72,
    background: "linear-gradient(90deg, #141826 0%, #1d2236 50%, #141826 100%)",
    borderRadius: top ? `${d * 0.06}px ${d * 0.06}px 0 0` : `0 0 ${d * 0.06}px ${d * 0.06}px`,
    // Fade the strap out so it reads as continuing past the frame edge.
    maskImage: `linear-gradient(${top ? "to top" : "to bottom"}, black 40%, transparent 100%)`,
  });

  return (
    <div
      style={{
        position: "absolute",
        left: g.cx - caseD / 2,
        top: g.cy - caseD / 2,
        width: caseD,
        height: caseD,
        transform: `scale(${scale})`,
      }}
    >
      <div style={strap(true)} />
      <div style={strap(false)} />

      {/* Crown */}
      <div
        style={{
          position: "absolute",
          left: caseD - ring * 0.25,
          top: caseD / 2 - d * 0.075,
          width: d * 0.06,
          height: d * 0.15,
          borderRadius: d * 0.02,
          background: "linear-gradient(180deg, #6b7086 0%, #b9bdcc 45%, #5a5e72 100%)",
        }}
      />

      {/* Case ring */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          borderRadius: "50%",
          background:
            "conic-gradient(from 210deg, #4a4f63, #c4c8d6, #5b6075, #9aa0b4, #464b5e, #c4c8d6, #4a4f63)",
          boxShadow: "0 40px 120px rgba(0,0,0,0.6), 0 0 0 2px rgba(143,166,255,0.12)",
        }}
      />

      {/* Bezel + screen */}
      <div
        style={{
          position: "absolute",
          left: ring,
          top: ring,
          width: d + 2 * bezel,
          height: d + 2 * bezel,
          borderRadius: "50%",
          background: "#04060b",
          padding: bezel,
          boxSizing: "border-box",
        }}
      >
        <div
          style={{
            // Positioned, so the segments' absolutely-positioned Sequences are
            // clipped to the round screen instead of escaping the overflow.
            position: "relative",
            width: "100%",
            height: "100%",
            borderRadius: "50%",
            overflow: "hidden",
            background: "#000",
          }}
        >
          <SegmentedVideo src={src} segments={segments} />
        </div>
      </div>
    </div>
  );
};

const CaptionView: React.FC<{ g: Geo; lead: string; accent: string }> = ({ g, lead, accent }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const inP = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 18 });
  const y = interpolate(inP, [0, 1], [28, 0]);
  return (
    <div
      style={{
        position: "absolute",
        left: g.capX - g.capW / 2,
        top: g.capY,
        width: g.capW,
        transform: `translateY(${y}px)`,
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

export const Watch: React.FC<{ layout: Layout; journey: WatchJourney }> = ({ layout, journey }) => {
  const { width, height, fps } = useVideoConfig();
  const g = geometry(layout, width, height);
  return (
    <AbsoluteFill
      style={{ background: `radial-gradient(120% 90% at 50% 20%, ${NAVY_2} 0%, ${NAVY} 62%)` }}
    >
      <WatchDevice g={g} src={journey.videoSrc} segments={journey.segments} />
      {journey.captions.map((c, i) => (
        <Sequence key={i} from={Math.round(c.at * fps)} durationInFrames={Math.round(c.dur * fps)}>
          <CaptionView g={g} lead={c.lead} accent={c.accent} />
        </Sequence>
      ))}
      <CtaOutro layout={layout} startSec={journey.outroStartSec} />
    </AbsoluteFill>
  );
};
