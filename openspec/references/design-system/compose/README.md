# Nubecita Compose — drop-in M3 theme

This folder is a **handoff package**, not a buildable project. Copy the files into your Android Studio project under `app/src/main/java/app/nubecita/` and adjust the package + import paths to match your namespace.

## File map

```
compose/
├── ui/theme/
│   ├── Color.kt      ← raw tonal palette (Sky / Peach / Lilac / Neutral)
│   ├── Theme.kt      ← M3 ColorScheme + NubecitaTheme { } wrapper
│   ├── Type.kt       ← M3 Typography (Fraunces + Roboto Flex + JetBrains Mono)
│   ├── Shape.kt      ← M3 Shapes + extra component shapes (pill button, sheet)
│   └── Tokens.kt     ← spacing scale + motion easings + LocalNubecita
└── ui/components/
    └── Avatar.kt     ← Threads-style avatar w/ follow badge + context menu
```

## 1 · Add the dependency

In `app/build.gradle.kts`:

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3:1.4.0-alpha10")  // M3 Expressive APIs
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
```

Material 3 Expressive ships in **material3 1.4.0-alpha10+**. Pin a stable once it lands in 2026.

## 2 · Add the fonts

Drop the variable font files into `app/src/main/res/font/`:
- `fraunces_variable.ttf` — https://fonts.google.com/specimen/Fraunces
- `roboto_flex_variable.ttf` — https://fonts.google.com/specimen/Roboto+Flex
- `jetbrains_mono.ttf` — https://www.jetbrains.com/lp/mono/

Then in `Type.kt`, swap the placeholder `FontFamily.Default` references for:
```kotlin
val Fraunces   = FontFamily(Font(R.font.fraunces_variable))
val RobotoFlex = FontFamily(Font(R.font.roboto_flex_variable))
val JetBrains  = FontFamily(Font(R.font.jetbrains_mono))
```

For the Fraunces `SOFT` axis, use `FontVariation.Setting("SOFT", 50f)` on `TextStyle.fontVariationSettings`.

## 3 · Wrap your Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NubecitaTheme(dynamicColor = false) {   // false = always brand palette
                NubecitaApp()
            }
        }
    }
}
```

## 4 · How tokens map

| CSS variable                    | Compose access                                          |
|--------------------------------|---------------------------------------------------------|
| `--primary`                     | `MaterialTheme.colorScheme.primary`                     |
| `--surface-container-low`       | `MaterialTheme.colorScheme.surfaceContainerLow`         |
| `--fg-2`                        | `MaterialTheme.colorScheme.onSurfaceVariant`            |
| `--shape-button`                | `NubecitaShape.Button`                                  |
| `--s-4` (16px)                  | `LocalNubecita.current.spacing.s4`                      |
| `--ease-spring-bouncy`          | `spring(NubecitaSpring.Bouncy.damping, …stiffness)`     |
| `body-large` (17sp)             | `MaterialTheme.typography.bodyLarge`                    |

## 5 · Tips

- **Stay inside M3 component APIs** wherever possible — `Button`, `FilledTonalButton`, `NavigationBar`, `NavigationRail`, `TopAppBar`, `FloatingActionButton`, `Card`. They already read your color/shape/typography from `MaterialTheme`.
- **Custom shapes** (pill button, sheet top): pass via `shape =` parameter or override at the theme level by editing `Shape.kt`.
- **Dark mode** is wired through `isSystemInDarkTheme()` — Settings toggle can override via state hoisted into `NubecitaTheme(darkTheme = ...)`.
- **WindowSizeClass** drives the adaptive layouts:
  - `compact` → bottom nav (`NavigationBar`)
  - `medium`  → nav rail (`NavigationRail`)
  - `expanded` → expanded nav rail + 3-pane (`Scaffold` + `Row`)
  Use `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold` to get this for free.
- **Bluesky API**: pair with [atproto-kotlin](https://github.com/bluesky-social/atproto-android) or the official `at://` libraries. The data model in `data.jsx` (post → name/handle/replies/reposts/likes/following) maps 1:1 to AT Proto's `app.bsky.feed.defs#postView`.

## 6 · What's missing (deliberate)

This package gives you the **theme + a sample component**, not a full app. Build the screens (Feed, PostDetail, Composer, Profile) using the Compose primitives — the HTML UI kit in `ui_kits/nubecita-android/Screens.jsx` is a faithful reference for layout, spacing, and behavior.

## 7 · Screens included

```
compose/
├── data/
│   └── FakeData.kt              ← Post data class + sample feed/replies
├── ui/components/
│   ├── Avatar.kt                ← avatar w/ follow badge + menu
│   └── PostCard.kt              ← reusable feed row
└── ui/screens/
    ├── FeedScreen.kt            ← TopAppBar + chip filters + feed list + FAB
    └── PostDetailScreen.kt      ← focused post + reply prompt + replies
```

Wire them up in your nav graph:

```kotlin
@Composable
fun NubecitaApp() {
    var route by remember { mutableStateOf<Route>(Route.Feed) }
    when (val r = route) {
        Route.Feed -> FeedScreen(
            onOpenPost     = { route = Route.PostDetail(it) },
            onOpenCompose  = { route = Route.Composer },
        )
        is Route.PostDetail -> PostDetailScreen(
            post     = r.post,
            onBack   = { route = Route.Feed },
            onReply  = { route = Route.Composer },
        )
        // …
    }
}

sealed interface Route {
    data object Feed : Route
    data class PostDetail(val post: Post) : Route
    data object Composer : Route
}
```

In a real app, swap this for `androidx.navigation:navigation-compose` with type-safe routes.
