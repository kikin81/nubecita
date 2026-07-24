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
