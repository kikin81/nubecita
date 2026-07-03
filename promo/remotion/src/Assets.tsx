import React from "react";
import { AbsoluteFill, Img, staticFile } from "remotion";
import { loadFont as loadFraunces } from "@remotion/google-fonts/Fraunces";

const { fontFamily: FRAUNCES } = loadFraunces();

const NAVY = "#0A0E1A";
const NAVY_2 = "#111731";
const ACCENT = "#8FA6FF";
const WHITE = "#F5F7FF";

const PHONE_ASPECT = 1280 / 2856; // w/h
const TABLET_ASPECT = 2560 / 1600; // w/h

type Orientation = "portrait" | "square" | "landscape";

export type AssetSpec = {
  id: string;
  width: number;
  height: number;
  orientation: Orientation;
  device: "phone" | "tablet";
  src: string; // file in public/
  lead: string;
  accent: string;
};

const DeviceFrame: React.FC<{
  w: number;
  h: number;
  aspect: number;
  cornerFactor: number;
  src: string;
}> = ({ w, h, aspect, cornerFactor, src }) => {
  const bezel = w * 0.022;
  const radius = w * cornerFactor;
  return (
    <div
      style={{
        width: w,
        height: h,
        borderRadius: radius,
        background: "#05070d",
        padding: bezel,
        boxShadow: "0 40px 110px rgba(0,0,0,0.55), 0 0 0 2px rgba(143,166,255,0.10)",
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
        <Img
          src={staticFile(src)}
          style={{ width: "100%", height: "100%", objectFit: "cover" }}
        />
      </div>
    </div>
  );
};

export const Asset: React.FC<AssetSpec> = ({
  width,
  height,
  orientation,
  device,
  src,
  lead,
  accent,
}) => {
  const bg = `radial-gradient(120% 90% at 50% 15%, ${NAVY_2} 0%, ${NAVY} 65%)`;

  if (orientation === "landscape") {
    // Tablet on the left, headline on the right.
    const dh = height * 0.82;
    const dw = dh * TABLET_ASPECT;
    return (
      <AbsoluteFill style={{ background: bg, flexDirection: "row", alignItems: "center" }}>
        <div style={{ marginLeft: width * 0.04 }}>
          <DeviceFrame w={dw} h={dh} aspect={TABLET_ASPECT} cornerFactor={0.045} src={src} />
        </div>
        <div
          style={{
            flex: 1,
            padding: `0 ${width * 0.05}px`,
            fontFamily: FRAUNCES,
            fontWeight: 600,
            fontSize: height * 0.115,
            lineHeight: 1.05,
            color: WHITE,
            letterSpacing: -1,
          }}
        >
          {lead} <span style={{ color: ACCENT }}>{accent}</span>
        </div>
      </AbsoluteFill>
    );
  }

  // Portrait / square: phone centered, headline in the bottom safe band.
  const capBand = orientation === "square" ? 0.2 : 0.16;
  const dh = height * (1 - capBand) * 0.92;
  const dw = dh * PHONE_ASPECT;
  return (
    <AbsoluteFill style={{ background: bg, alignItems: "center" }}>
      <div style={{ marginTop: height * 0.03 }}>
        <DeviceFrame w={dw} h={dh} aspect={PHONE_ASPECT} cornerFactor={0.14} src={src} />
      </div>
      <div
        style={{
          position: "absolute",
          bottom: height * 0.045,
          width: width * 0.9,
          textAlign: "center",
          fontFamily: FRAUNCES,
          fontWeight: 600,
          fontSize: height * (orientation === "square" ? 0.062 : 0.05),
          lineHeight: 1.05,
          color: WHITE,
          letterSpacing: -0.5,
        }}
      >
        {lead} <span style={{ color: ACCENT }}>{accent}</span>
      </div>
    </AbsoluteFill>
  );
};

// The full asset matrix: phone (portrait 4:5 + square 1:1) and tablet (landscape 1.91:1).
export const ASSET_SPECS: AssetSpec[] = [
  // Phone — Portrait 4:5 (1200x1500)
  { id: "phone-feed-45", width: 1200, height: 1500, orientation: "portrait", device: "phone", src: "feed.png", lead: "Your feed,", accent: "buttery smooth." },
  { id: "phone-search-45", width: 1200, height: 1500, orientation: "portrait", device: "phone", src: "search.png", lead: "Find", accent: "who to follow." },
  { id: "phone-profile-45", width: 1200, height: 1500, orientation: "portrait", device: "phone", src: "profile.png", lead: "Profiles that feel", accent: "native." },
  // Phone — Square 1:1 (1200x1200)
  { id: "phone-feed-11", width: 1200, height: 1200, orientation: "square", device: "phone", src: "feed.png", lead: "A faster", accent: "Bluesky." },
  { id: "phone-post-11", width: 1200, height: 1200, orientation: "square", device: "phone", src: "post.png", lead: "Threads,", accent: "beautifully." },
  // Tablet — Landscape 1.91:1 (1200x628)
  { id: "tablet-detail-19", width: 1200, height: 628, orientation: "landscape", device: "tablet", src: "tablet_detail.png", lead: "Built for", accent: "tablets, too." },
  { id: "tablet-feed-19", width: 1200, height: 628, orientation: "landscape", device: "tablet", src: "tablet_feed.png", lead: "Two panes,", accent: "more context." },
];
