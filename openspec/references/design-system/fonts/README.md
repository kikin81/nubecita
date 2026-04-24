# Fonts

Nubecita loads all three brand fonts from **Google Fonts** via `@import` in `colors_and_type.css`. No local font files are required to preview or use this design system.

| Font | Role | Source |
|---|---|---|
| **Fraunces** (variable, `SOFT 9..144`) | Display — big moments, onboarding, empty states | [Google Fonts](https://fonts.google.com/specimen/Fraunces) |
| **Roboto Flex** (variable, weight 300..900) | Body + headlines + UI | [Google Fonts](https://fonts.google.com/specimen/Roboto+Flex) |
| **JetBrains Mono** (400, 500, 600) | Handles, DIDs, dev | [Google Fonts](https://fonts.google.com/specimen/JetBrains+Mono) |
| **Material Symbols Rounded** (variable) | Icons | [Google Fonts](https://fonts.google.com/icons) |

## To bundle locally (optional)

If you want these offline or self-hosted, download `.woff2` from Google Fonts and drop them here, then swap the `@import` in `colors_and_type.css` for `@font-face` rules pointing at `./fonts/<file>.woff2`.
