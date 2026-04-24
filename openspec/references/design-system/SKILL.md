---
name: nubecita-design
description: Use this skill to generate well-branded interfaces and assets for Nubecita, either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping a Material 3 Expressive Android Bluesky client with adaptive layouts for phone, tablet, and foldables.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. Always reference `colors_and_type.css` for tokens, pull SVG marks from `assets/`, and use the JSX primitives in `ui_kits/nubecita-android/` as the starting point for any Nubecita screen.

If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert designer who outputs HTML artifacts — or production code — depending on the need.

Key things to remember when designing for Nubecita:
- Primary color is **Sky** `#0A7AFF` (tone 50). Secondary is Peach, tertiary is Lilac.
- Buttons are **fully pill-shaped** (M3 Expressive tell).
- Feed body type is **17 / 26 px** (one step larger than M3 default) because readability is the top priority.
- Display type is **Fraunces** with `SOFT 50–80` variation; body is **Roboto Flex**.
- Motion uses spring easing — default is a quick overshoot, emphasized is a larger slower overshoot.
- Iconography is **Material Symbols Rounded** — filled variant for active state.
- Voice: warm, direct, second-person, sentence case, no emoji in chrome.
