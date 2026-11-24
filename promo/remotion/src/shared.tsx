import React from "react";
import {
  AbsoluteFill,
  Img,
  OffthreadVideo,
  Sequence,
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

// A slice of a capture, in source seconds, played back to back with the others
// at `rate`. Captures run long (slow typing, bench delays), so a cut speeds up
// and skips the dead time.
export type Segment = { from: number; to: number; rate: number };

// Output length of the segments, in seconds.
export const segmentsDuration = (segments: Segment[]) =>
  segments.reduce((sum, s) => sum + (s.to - s.from) / s.rate, 0);

// Plays `segments` of `src` back to back from frame 0. The last segment stays
// up past its end (a frozen last frame) so a cut can hold or fade over it.
export const SegmentedVideo: React.FC<{ src: string; segments: Segment[] }> = ({ src, segments }) => {
  const { fps } = useVideoConfig();
  let at = 0;
  return (
    <>
      {segments.map((seg, i) => {
        const len = Math.round(((seg.to - seg.from) / seg.rate) * fps);
        const from = at;
        at += len;
        return (
          <Sequence key={i} from={from} durationInFrames={i === segments.length - 1 ? undefined : len}>
            <OffthreadVideo
              src={staticFile(src)}
              trimBefore={Math.round(seg.from * fps)}
              playbackRate={seg.rate}
              muted
              style={{ width: "100%", height: "100%", objectFit: "cover" }}
            />
          </Sequence>
        );
      })}
    </>
  );
};
