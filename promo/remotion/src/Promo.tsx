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
import { PALETTE, FRAUNCES, Layout, CtaOutro } from "./shared";

export type { Layout } from "./shared";

const { NAVY, NAVY_2, ACCENT, WHITE } = PALETTE;
export type DeviceKind = "phone" | "tablet";

export type Caption = { at: number; dur: number; lead: string; accent: string };

export type Journey = {
  id: string;
  device: DeviceKind;
  videoSrc: string; // file in public/
  sourceAspect: number; // width / height of the capture
  outroStartSec: number; // when the CTA outro fades in
  captions: Caption[];
};

type Geo = {
  devW: number;
  devH: number;
  devX: number;
  devY: number;
  capX: number;
  capY: number;
  capW: number;
  capAlign: "center" | "left";
  capSize: number;
};

const geometry = (layout: Layout, device: DeviceKind, aspect: number, w: number, h: number): Geo => {
  if (device === "tablet") {
    // Landscape device centered; caption in a bottom band.
    const maxW = w * 0.9;
    const maxH = h * 0.68;
    let devW = maxW;
    let devH = devW / aspect;
    if (devH > maxH) {
      devH = maxH;
      devW = devH * aspect;
    }
    return {
      devW,
      devH,
      devX: w / 2,
      devY: h * 0.42,
      capX: w / 2,
      capY: layout === "wide" ? h * 0.86 : h * 0.82,
      capW: w * 0.88,
      capAlign: "center",
      capSize: layout === "wide" ? 68 : 60,
    };
  }

  // phone (portrait source)
  if (layout === "vertical") {
    const devH = Math.min(h * 0.76, 1460);
    return {
      devH,
      devW: devH * aspect,
      devX: w / 2,
      devY: h * 0.42,
      capX: w / 2,
      capY: h * 0.9,
      capW: w * 0.86,
      capAlign: "center",
      capSize: 72,
    };
  }
  if (layout === "square") {
    const devH = h * 0.86;
    return {
      devH,
      devW: devH * aspect,
      devX: w * 0.31,
      devY: h / 2,
      capX: w * 0.64,
      capY: h * 0.5,
      capW: w * 0.4,
      capAlign: "left",
      capSize: 66,
    };
  }
  // wide
  const devH = h * 0.84;
  return {
    devH,
    devW: devH * aspect,
    devX: w * 0.28,
    devY: h / 2,
    capX: w * 0.62,
    capY: h * 0.5,
    capW: w * 0.42,
    capAlign: "left",
    capSize: 88,
  };
};

const DeviceFrame: React.FC<{
  g: Geo;
  device: DeviceKind;
  src: string;
  frame: number;
  fps: number;
}> = ({ g, device, src, frame, fps }) => {
  const enter = spring({ frame, fps, config: { damping: 200 }, durationInFrames: 24 });
  const scale = interpolate(enter, [0, 1], [0.94, 1]);
  const bezel = g.devW * (device === "tablet" ? 0.02 : 0.026);
  const radius = g.devW * (device === "tablet" ? 0.045 : 0.15);
  return (
    <div
      style={{
        position: "absolute",
        left: g.devX - g.devW / 2,
        top: g.devY - g.devH / 2,
        width: g.devW,
        height: g.devH,
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
          src={staticFile(src)}
          style={{ width: "100%", height: "100%", objectFit: "cover" }}
        />
      </div>
    </div>
  );
};

const CaptionView: React.FC<{ g: Geo; lead: string; accent: string; fps: number }> = ({
  g,
  lead,
  accent,
  fps,
}) => {
  const frame = useCurrentFrame();
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

export const Promo: React.FC<{ layout: Layout; journey: Journey }> = ({ layout, journey }) => {
  const frame = useCurrentFrame();
  const { width, height, fps } = useVideoConfig();
  const g = geometry(layout, journey.device, journey.sourceAspect, width, height);

  return (
    <AbsoluteFill
      style={{ background: `radial-gradient(120% 90% at 50% 12%, ${NAVY_2} 0%, ${NAVY} 62%)` }}
    >
      <DeviceFrame g={g} device={journey.device} src={journey.videoSrc} frame={frame} fps={fps} />

      {journey.captions.map((c, i) => (
        <Sequence key={i} from={Math.round(c.at * fps)} durationInFrames={Math.round(c.dur * fps)}>
          <CaptionView g={g} lead={c.lead} accent={c.accent} fps={fps} />
        </Sequence>
      ))}

      <CtaOutro layout={layout} startSec={journey.outroStartSec} />
    </AbsoluteFill>
  );
};
