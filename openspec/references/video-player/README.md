# Fullscreen video player — design reference

Source design for the `revamp-fullscreen-video-player` OpenSpec change. Authored
in Claude (the original `claudeusercontent.com` design URL is session-gated and
404s outside the author's session, so these PNGs are the durable copies). They
are the rendered pages of the original 4-panel "Video Player Controls" print
sheet; the PDF source is omitted because the renders are sharper, smaller, and
preview inline in GitHub.

- `controls-1.png` — **Canonical layout**: dark scrim · wavy bar · large round
  play · connected action group + author chip.
- `controls-2.png` — **Play/pause shape study**: A round · B squircle ·
  **C morph round→squircle on press** (the chosen variant).
- `controls-3.png` — **Action group & avatar placement**: A avatar-in-group ·
  **B avatar-as-author-chip** (chosen) · C vertical rail (out of scope).
- `controls-4.png` — **Scrim auto-hide ladder**: Shown → Peeking → Hidden.
- `canonical-mock.png` — the on-device screenshot of the canonical state.

Intentional divergence from the mock: mute + PiP are relocated from the top bar
into a utility row under play/pause (player controls, not post actions); the
cast icon is omitted (Chromecast is a separate epic). See the change's
`proposal.md` / `design.md`.
