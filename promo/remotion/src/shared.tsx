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
