import { Composition } from "remotion";
import { Promo } from "./Promo";
import { Asset, ASSET_SPECS } from "./Assets";

// Source screen recording is 640x1428 (device 1280x2856, aspect ≈ 0.4482),
// 60fps, ~17.85s. We give the comp a 20s runway (video + a short CTA outro).
const FPS = 60;
const DURATION = 20 * FPS;

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="PromoVertical"
        component={Promo}
        durationInFrames={DURATION}
        fps={FPS}
        width={1080}
        height={1920}
        defaultProps={{ layout: "vertical" as const }}
      />
      <Composition
        id="PromoSquare"
        component={Promo}
        durationInFrames={DURATION}
        fps={FPS}
        width={1080}
        height={1080}
        defaultProps={{ layout: "square" as const }}
      />
      <Composition
        id="PromoWide"
        component={Promo}
        durationInFrames={DURATION}
        fps={FPS}
        width={1920}
        height={1080}
        defaultProps={{ layout: "wide" as const }}
      />

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
