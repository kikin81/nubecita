import React from "react";
import {
  AbsoluteFill,
  Img,
  OffthreadVideo,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
  interpolate,
  spring,
  Sequence,
} from "remotion";
import { loadFont as loadFraunces } from "@remotion/google-fonts/Fraunces";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";

const { fontFamily: FRAUNCES } = loadFraunces();
const { fontFamily: INTER } = loadInter();

// Brand palette (matches the app's dark-navy + periwinkle accent).
const NAVY = "#0A0E1A";
const NAVY_2 = "#111731";
const ACCENT = "#8FA6FF";
const WHITE = "#F5F7FF";

const PHONE_ASPECT = 640 / 1428; // width / height of the source recording

type Layout = "vertical" | "square" | "wide";

const geometry = (layout: Layout, w: number, h: number) => {
  if (layout === "vertical") {
    const phoneH = Math.min(h * 0.76, 1460);
    return {
      phoneH,
      phoneW: phoneH * PHONE_ASPECT,
      phoneX: w / 2,
      phoneY: h * 0.42,
      capX: w / 2,
      capY: h * 0.9,
      capW: w * 0.86,
      capAlign: "center" as const,
      capSize: 72,
    };
  }
  if (layout === "square") {
    const phoneH = h * 0.86;
    return {
      phoneH,
      phoneW: phoneH * PHONE_ASPECT,
      phoneX: w * 0.31,
      phoneY: h / 2,
      capX: w * 0.64,
      capY: h * 0.5,
      capW: w * 0.4,
      capAlign: "left" as const,
      capSize: 66,
    };
  }
  // wide
  const phoneH = h * 0.84;
  return {
    phoneH,
    phoneW: phoneH * PHONE_ASPECT,
    phoneX: w * 0.28,
    phoneY: h / 2,
    capX: w * 0.62,
    capY: h * 0.5,
    capW: w * 0.42,
    capAlign: "left" as const,
    capSize: 88,
  };
};

const Phone: React.FC<{ g: ReturnType<typeof geometry>; frame: number; fps: number }> = ({
  g,
  frame,
  fps,
}) => {
  const enter = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 24 });
  const scale = interpolate(enter, [0, 1], [0.94, 1]);
  const bezel = g.phoneW * 0.026;
  const radius = g.phoneW * 0.15;
  return (
    <div
      style={{
        position: "absolute",
        left: g.phoneX - g.phoneW / 2,
        top: g.phoneY - g.phoneH / 2,
        width: g.phoneW,
        height: g.phoneH,
        transform: `scale(${scale})`,
        borderRadius: radius,
        background: "#05070d",
        padding: bezel,
        boxShadow: "0 40px 120px rgba(0,0,0,0.55), 0 0 0 2px rgba(143,166,255,0.10)",
      }}
    >
      <div
        style={{
          width: "100%",
          height: "100%",
          borderRadius: radius - bezel,
          overflow: "hidden",
          background: "#000",
        }}
      >
        <OffthreadVideo
          src={staticFile("promo.mp4")}
          style={{ width: "100%", height: "100%", objectFit: "cover" }}
        />
      </div>
    </div>
  );
};

const Caption: React.FC<{
  g: ReturnType<typeof geometry>;
  lead: string;
  accent: string;
  fps: number;
}> = ({ g, lead, accent, fps }) => {
  const frame = useCurrentFrame();
  const inP = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 18 });
  const y = interpolate(inP, [0, 1], [28, 0]);
  return (
    <div
      style={{
        position: "absolute",
        left: g.capAlign === "center" ? g.capX - g.capW / 2 : g.capX - g.capW / 2,
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

export const Promo: React.FC<{ layout: Layout }> = ({ layout }) => {
  const frame = useCurrentFrame();
  const { width, height, fps, durationInFrames } = useVideoConfig();
  const g = geometry(layout, width, height);

  // CTA outro fades in over the last stretch.
  const outroStart = 16.2 * fps;
  const outro = interpolate(frame, [outroStart, outroStart + 0.5 * fps], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });

  return (
    <AbsoluteFill
      style={{
        background: `radial-gradient(120% 90% at 50% 12%, ${NAVY_2} 0%, ${NAVY} 62%)`,
      }}
    >
      <Phone g={g} frame={frame} fps={fps} />

      {/* Beat-synced captions */}
      <Sequence from={Math.round(0.4 * fps)} durationInFrames={Math.round(3.6 * fps)}>
        <Caption g={g} lead="One tap to" accent="like." fps={fps} />
      </Sequence>
      <Sequence from={Math.round(4.3 * fps)} durationInFrames={Math.round(4.9 * fps)}>
        <Caption g={g} lead="120 fps." accent="Zero jank." fps={fps} />
      </Sequence>
      <Sequence from={Math.round(9.5 * fps)} durationInFrames={Math.round(5.7 * fps)}>
        <Caption g={g} lead="Feed · Search · Chats ·" accent="You." fps={fps} />
      </Sequence>

      {/* CTA outro */}
      <AbsoluteFill
        style={{
          opacity: outro,
          background: `radial-gradient(120% 90% at 50% 40%, ${NAVY_2} 0%, ${NAVY} 70%)`,
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
            color: WHITE,
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
            color: ACCENT,
            opacity: 0.95,
          }}
        >
          A fast, native Bluesky client
        </div>
        {/* Official Google Play badge — used unmodified (uniform scale only),
            per the Play badge guidelines. */}
        <Img
          src={staticFile("gp_badge.png")}
          style={{ height: layout === "wide" ? 112 : 100, width: "auto", marginTop: 16 }}
        />
      </AbsoluteFill>

      {/* keep durationInFrames referenced for clarity */}
      {frame > durationInFrames ? null : null}
    </AbsoluteFill>
  );
};
