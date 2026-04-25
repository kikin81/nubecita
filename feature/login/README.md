# `:feature:login`

OAuth login feature. Two-module pair following Google's Navigation 3
modularization recipe (`developer.android.com/guide/navigation/navigation-3/modularize`).

## `:feature:login:api`

`@Serializable data object Login : NavKey`. Nothing else.

Other feature modules that want to navigate to login depend on
`:feature:login:api` alone — never on `:feature:login:impl` — so the api
module's classpath stays minimal: just `navigation3-runtime` and
`kotlinx-serialization-json`.

## `:feature:login:impl`

- `LoginScreen` — Compose UI: handle field + submit button + inline
  loading / error. No password field, no app-password toggle.
- `LoginViewModel` — `@HiltViewModel`, MVI on top of
  `:core:common`'s `MviViewModel<S, E, F>`. Drives
  `AuthRepository.beginLogin(handle)`. On success, emits
  `LoginEffect.LaunchCustomTab(url)`. The Custom Tab launch and the
  redirect-callback `completeLogin` path land under `nubecita-ck0`.
- `di/LoginNavigationModule` — `@Provides @IntoSet EntryProviderInstaller`
  registering `entry<Login> { LoginScreen() }` with the Nav 3 entry
  provider scope. `:app` collects the multibinding through a Hilt
  `EntryPoint` and invokes every installer inside `NavDisplay`.

## How it wires up

```
:app::MainNavigation
  └─ NavDisplay
      ├─ entryDecorators = [saveable, viewModelStore]   ← from nav3-runtime + lifecycle-viewmodel-navigation3
      └─ entryProvider {
           entry<Main> { ... }                          ← :app's placeholder destination
           installers.forEach { it() }                  ← contributed by every :feature:*:impl
         }
```

When `:app` adds `implementation(project(":feature:login:impl"))`, Hilt's
`SingletonComponent` picks up `LoginNavigationModule`'s `@IntoSet`
contribution. `MainNavigation` reads the set via
`NavigationEntryPoint.entryProviderInstallers()` and invokes every
member, registering all feature destinations.

## Adding the next feature

```bash
mkdir -p feature/<name>/{api,impl}
```

Copy `feature/login/api/build.gradle.kts` and `feature/login/impl/build.gradle.kts`
verbatim, change the namespaces, add the new include lines to
`settings.gradle.kts`, and add `implementation(project(":feature:<name>:impl"))`
to `:app/build.gradle.kts`. The convention plugins handle SDK / Compose /
Hilt wiring; you just declare a `NavKey` in `:api` and a
`@Provides @IntoSet EntryProviderInstaller` in `:impl`.
```
