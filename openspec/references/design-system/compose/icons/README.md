# Nubecita Icon Set

**58 icons**, all 24×24 viewBox, drawn in a consistent style:
- 1.75 stroke width, rounded line caps + joins
- `currentColor` stroke (so they pick up `Color`/`tint` in Compose)
- Filled variants (`*_fill.svg`) included for stateful icons (heart, bookmark, bell, cloud, home)

Preview at `compose/icons/index.html`.

## Generate Compose ImageVectors with Valkyrie

[Valkyrie](https://github.com/ComposeGears/Valkyrie) is a JetBrains plugin / CLI that turns SVGs into `ImageVector` Kotlin code.

### Plugin (recommended)

1. Install **Valkyrie** in Android Studio: Settings → Plugins → search "Valkyrie" → Install.
2. Open the Valkyrie tool window → **SVG/XML to ImageVector**.
3. Drag the entire `compose/icons/` folder in.
4. Set:
   - **Package**: `app.nubecita.ui.icons`
   - **Output kind**: ImageVector
   - **Icon pack name**: `NubecitaIcons`
   - **Naming**: `ic_chat_bubble` → `ChatBubble` (auto-strips `ic_` prefix and pascal-cases)
5. Generate. You'll get `NubecitaIcons.ChatBubble`, `NubecitaIcons.HeartFill`, etc.

### CLI

```bash
brew install --cask valkyrie    # or download from releases
valkyrie svg-to-image-vector \
  --input  ./compose/icons \
  --output ./app/src/main/java/app/nubecita/ui/icons \
  --package app.nubecita.ui.icons \
  --icon-pack NubecitaIcons \
  --strip-prefix ic_
```

## Use in Compose

```kotlin
import app.nubecita.ui.icons.NubecitaIcons
import app.nubecita.ui.icons.nubecitaicons.ChatBubble
import app.nubecita.ui.icons.nubecitaicons.HeartFill

Icon(
    imageVector = NubecitaIcons.ChatBubble,
    contentDescription = "Reply",
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(20.dp),
)
```

To swap them into the screens I already wrote, replace the `Icons.Outlined.*` references in `FeedScreen.kt` / `PostDetailScreen.kt` / `PostCard.kt` with `NubecitaIcons.*`.

## What's bundled

| Group        | Icons |
|--------------|-------|
| Navigation   | home (+ fill), search, menu, arrow_back / forward, chevron_left / right / up / down, close, more_vert, more_horiz |
| Post actions | heart (+ fill), chat_bubble, repeat, share, bookmark (+ fill) |
| Compose      | edit, compose, image, gif, mood, mention, send, attach |
| People       | person, person_add, group, check, plus, minus |
| Status       | bell (+ fill) |
| Settings     | settings, palette, shield, lock, language, text_fields, info, help, download, upload, logout |
| Brand & misc | cloud (+ fill), sparkle, auto_awesome, memory, menu_book, globe, eye (+ off), flag, pin |

If you need more (camera, mic, video, link, location, hashtag, etc.) just say which and I'll add them in the same style.
