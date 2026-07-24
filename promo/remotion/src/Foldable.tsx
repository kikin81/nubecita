import React from "react";
import {
  AbsoluteFill,
  Img,
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
  coverSrc: string; // fold_cover.mp4       (aspect ~0.516, scrolling feed)
  innerSrc: string; // fold_inner.png still (aspect ~1.20, filled two-pane)
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

const PERSPECTIVE = 2600; // tune in Studio

// One half of the inner display — a bezelled panel. The left half is static;
// the right half is hinged at the spine (its inner edge) and rotates on rotateY.
// Each shows its half of the filled two-pane still (full-width image offset +
// overflow clip). A static still (not video) so the detail pane stays filled
// throughout the open phase (the capture's filled window is only ~1.8s).
const HalfScreen: React.FC<{
  src: string;
  side: "left" | "right";
  halfW: number; // this panel's width (left/right differ so the crease lands on the app divider)
  dh: number;
  radius: number;
  bezel: number;
  crease: number;
  imgWidth: number; // the full two-pane image width; the two screens tile it
  imgLeft: number; // this screen's x-offset into that image
  rot: number;
}> = ({ src, side, halfW, dh, radius, bezel, crease, imgWidth, imgLeft, rot }) => {
  const isLeft = side === "left";
  return (
    <div
      style={{
        width: halfW,
        height: dh,
        boxSizing: "border-box",
        background: "#05070d",
        paddingTop: bezel,
        paddingBottom: bezel,
        // full bezel on the OUTER edge, a hairline on the SPINE edge
        paddingLeft: isLeft ? bezel : crease,
        paddingRight: isLeft ? crease : bezel,
        // round only the OUTER corners; the spine (inner) edge stays square
        borderTopLeftRadius: isLeft ? radius : 0,
        borderBottomLeftRadius: isLeft ? radius : 0,
        borderTopRightRadius: isLeft ? 0 : radius,
        borderBottomRightRadius: isLeft ? 0 : radius,
        transformOrigin: isLeft ? "right center" : "left center",
        transform: `rotateY(${rot}deg)`,
        backfaceVisibility: "hidden",
        // subtle crease shadow along the spine edge + a soft drop shadow
        boxShadow:
          (isLeft
            ? "inset -5px 0 14px -12px rgba(0,0,0,0.85)"
            : "inset 5px 0 14px -12px rgba(0,0,0,0.85)") +
          ", 0 26px 55px rgba(0,0,0,0.42)",
      }}
    >
      <div
        style={{
          width: "100%",
          height: "100%",
          overflow: "hidden",
          position: "relative",
          background: "#000",
          borderTopLeftRadius: isLeft ? radius - bezel : 0,
          borderBottomLeftRadius: isLeft ? radius - bezel : 0,
          borderTopRightRadius: isLeft ? 0 : radius - bezel,
          borderBottomRightRadius: isLeft ? 0 : radius - bezel,
        }}
      >
        <Img
          src={staticFile(src)}
          style={{ position: "absolute", top: 0, left: imgLeft, width: imgWidth, height: "100%", objectFit: "cover" }}
        />
      </div>
    </div>
  );
};

// The folded cover display: a portrait slab (cover aspect) shown while closed.
const CoverSlab: React.FC<{ src: string; w: number; h: number; radius: number; bezel: number }> = ({
  src,
  w,
  h,
  radius,
  bezel,
}) => (
  <div
    style={{
      width: w,
      height: h,
      background: "#05070d",
      padding: bezel,
      boxSizing: "border-box",
      borderRadius: radius,
      boxShadow: "0 40px 120px rgba(0,0,0,0.55), 0 0 0 2px rgba(143,166,255,0.10)",
    }}
  >
    <div style={{ width: "100%", height: "100%", overflow: "hidden", borderRadius: radius - bezel, background: "#000" }}>
      <OffthreadVideo src={staticFile(src)} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
    </div>
  </div>
);

// The hinge: folded cover crossfades into the two-pane as the right half sweeps
// open on the spine. `unfold` 0 = closed (cover shown), 1 = flat open (two-pane).
const FoldingDevice: React.FC<{ journey: FoldableJourney; dw: number; dh: number; unfold: number }> = ({
  journey,
  dw,
  dh,
  unfold,
}) => {
  const radius = dw * 0.045;
  const bezel = dw * 0.02;
  const rightRot = interpolate(unfold, [0, 1], [-155, 0]); // deg, swings on the spine
  const coverOpacity = interpolate(unfold, [0, 0.42], [1, 0], { extrapolateRight: "clamp" });
  const innerOpacity = interpolate(unfold, [0.32, 0.6], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const glare = interpolate(unfold, [0.2, 0.9], [-1.1, 1.4], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  // Split the panels at the app's actual list/detail divider (~52% of the
  // source frame), not at 50% — otherwise the right panel shows a sliver of the
  // list pane at the crease. The crease then falls exactly on the app's gutter.
  const crease = dw * 0.004;
  const SPLIT = 0.52;
  const contentW = dw - 2 * bezel - 2 * crease; // the two inner screens tile this
  const leftInnerW = contentW * SPLIT;
  const leftW = leftInnerW + bezel + crease;
  const rightW = dw - leftW;

  // cover slab: portrait, half the open width, centered on the device center
  const coverW = dw / 2;
  const coverH = coverW / journey.coverAspect;

  return (
    <div style={{ position: "relative", width: dw, height: dh, perspective: PERSPECTIVE }}>
      {/* open inner two-pane — fades in as it opens; right half rides the hinge */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          display: "flex",
          opacity: innerOpacity,
          transformStyle: "preserve-3d",
        }}
      >
        <HalfScreen
          src={journey.innerSrc}
          side="left"
          halfW={leftW}
          dh={dh}
          radius={radius}
          bezel={bezel}
          crease={crease}
          imgWidth={contentW}
          imgLeft={0}
          rot={0}
        />
        <HalfScreen
          src={journey.innerSrc}
          side="right"
          halfW={rightW}
          dh={dh}
          radius={radius}
          bezel={bezel}
          crease={crease}
          imgWidth={contentW}
          imgLeft={-leftInnerW}
          rot={rightRot}
        />
      </div>

      {/* folded cover slab — centered; fades out as it opens */}
      <div
        style={{
          position: "absolute",
          left: dw / 2 - coverW / 2,
          top: dh / 2 - coverH / 2,
          width: coverW,
          height: coverH,
          opacity: coverOpacity,
        }}
      >
        <CoverSlab src={journey.coverSrc} w={coverW} h={coverH} radius={radius} bezel={bezel} />
      </div>

      {/* glass catch-light sweep */}
      <div
        style={{
          position: "absolute",
          inset: 0,
          pointerEvents: "none",
          background: "linear-gradient(105deg, transparent 0%, rgba(255,255,255,0.10) 50%, transparent 100%)",
          transform: `translateX(${glare * dw}px)`,
          mixBlendMode: "screen",
          opacity: innerOpacity,
          borderRadius: radius,
        }}
      />
    </div>
  );
};

export const Foldable: React.FC<{ layout: Layout; journey: FoldableJourney }> = ({
  layout,
  journey,
}) => {
  const { width, height, fps } = useVideoConfig();
  const frame = useCurrentFrame();
  const { dw, dh } = fitInner(journey.innerAspect, width, height);

  // unfold: 0 before foldStartSec, springs to 1 over foldDurSec
  const unfold = spring({
    frame: frame - journey.foldStartSec * fps,
    fps,
    config: { damping: 200 },
    durationInFrames: journey.foldDurSec * fps,
  });

  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(120% 90% at 50% 12%, ${PALETTE.NAVY_2} 0%, ${PALETTE.NAVY} 62%)`,
      }}
    >
      <div style={{ position: "absolute", left: width / 2 - dw / 2, top: height * 0.4 - dh / 2 }}>
        <FoldingDevice journey={journey} dw={dw} dh={dh} unfold={unfold} />
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
