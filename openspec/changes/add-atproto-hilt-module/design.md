## Context

`nubecita-6il` landed the `io.github.kikin81.atproto:{models,runtime,oauth}:5.0.0` artifacts on the classpath, but no code in the app constructs an `XrpcClient` yet. Feature work already in flight (the feed, handle-lookup flows, profile views) needs to call AT Protocol lexicons against the public AppView before any login work is done.

The Hilt graph is established (`NubecitaApplication` is `@HiltAndroidApp`, `MainActivity` is `@AndroidEntryPoint`, `DataModule` demonstrates the `@Module @InstallIn(SingletonComponent::class)` pattern with a `@Binds` for `DataRepository`). This change bolts on a second module in the same style.

The library's own `atproto-setup` skill spells out the construction surface: `XrpcClient(baseUrl, httpClient, json = DefaultJson, authProvider = NoAuth)` in `io.github.kikin81.atproto.runtime`. A caller-supplied Ktor `HttpClient` owns the engine, timeouts, retries, and logging; the client itself handles serialization internally. Omitting the `authProvider` gives you anonymous calls — exactly what `com.atproto.identity.resolveHandle` and public AppView reads need.

## Goals / Non-Goals

**Goals:**
- Feature code can `@Inject` an `XrpcClient` and issue anonymous queries without knowing about Ktor, base URLs, or the atproto-kotlin library's construction surface.
- The module integrates cleanly with future authenticated bindings (`nubecita-4g7`) without rework.
- Smoke-test confirms the wiring actually reaches Bluesky (`resolveHandle bsky.app → did:plc:…`).

**Non-Goals:**
- Authenticated `XrpcClient` / `AtOAuth` / `OAuthSessionStore` wiring — split into `nubecita-4g7`.
- Instrumented `@HiltAndroidTest` that `@Inject`s the client — split into `nubecita-16a`.
- BuildConfig-flavored or user-configurable base URLs — not needed until per-user PDS routing lands with auth.
- Ktor plugins (logging, content negotiation) on the shared `HttpClient`.

## Decisions

### Ktor CIO over OkHttp or Darwin

Options considered:
- `ktor-client-cio` — pure Kotlin, works on Android + JVM + native, no platform dependencies.
- `ktor-client-okhttp` — OkHttp-backed, slightly better on Android (connection pooling, HTTP/2 negotiation tuning OkHttp has refined for years).
- `ktor-client-darwin` — iOS only; irrelevant here.

**Choice: CIO.** It's the default the library's own `atproto-setup` skill and sample Android app (`samples/android/.../AppModule.kt`) use. Keeping parity with the library's sample means bugs and quirks we hit will match what upstream sees. OkHttp can swap in later if we identify an Android-specific need — the `HttpClient` is behind a Hilt provider, so the swap is one module edit. Pure-Kotlin CIO also keeps the JVM unit test trivially runnable (no Android framework on the classpath for the test).

### Shared `HttpClient` across anonymous and (future) authenticated clients

Options considered:
- One `HttpClient` singleton reused for both anonymous (`XrpcClient`) and authenticated (`AtOAuth`-backed client) flows.
- Separate `HttpClient` instances, one per flow, potentially `@Named` qualified.

**Choice: one shared singleton.** The authenticated path's distinguishing work (DPoP proof-of-possession, auth header injection, nonce rotation on 401) lives inside the library's `AuthProvider` abstraction — it's attached per-call on `XrpcClient`, not baked into the `HttpClient`. The `HttpClient` is a pure transport; no flow-specific state belongs on it. Sharing halves the socket pool footprint and matches what the library's sample does.

**Trade-off:** if we ever need to install a client-level plugin that only makes sense for one flow (e.g., a logging interceptor for unauthenticated diagnostics that we don't want dumping auth headers), we'd have to split. Acceptable — we have no such need today, and splitting later is a one-file edit.

### `baseUrl = "https://public.api.bsky.app"` (public AppView)

Options considered:
- `https://bsky.social` — canonical Bluesky PDS.
- `https://public.api.bsky.app` — Bluesky's public AppView gateway.
- `https://api.bsky.app` — an alternate AppView entry.

**Choice: `public.api.bsky.app`.** The "public" subdomain is Bluesky's explicit affordance for unauthenticated client reads; it serves the identity lexicons (including `resolveHandle`) and feed/profile reads without requiring per-user PDS discovery first. Using a PDS URL would work for a handful of lexicons but would force us to think about per-user routing before we're ready. The AppView is exactly the right abstraction for the anonymous case.

**Trade-off:** writes and some authenticated queries must still go through the user's PDS — that's the authenticated flow's concern in `nubecita-4g7` and will use a different `baseUrl` derived from the session. Two `XrpcClient` instances will coexist; fine.

### Module placement: `data/AtProtoModule.kt`

Options considered:
- `data/AtProtoModule.kt` — sibling of `DataModule.kt`, matches existing convention.
- `data/atproto/AtProtoModule.kt` — subpackage anticipating growth (oauth module, session store, etc.).
- `di/AtProtoModule.kt` — a dedicated DI package.

**Choice: flat `data/AtProtoModule.kt`.** One file, matches what's there. If the atproto surface grows (it will, with `nubecita-4g7`), we can introduce `data/atproto/` then without blocking this change.

### JVM unit test over instrumented `@HiltAndroidTest`

Options considered:
- JVM unit test in `app/src/test/` that constructs `XrpcClient` directly, no Hilt.
- `@HiltAndroidTest` in `app/src/androidTest/` with `HiltAndroidRule`, real graph, emulator.
- Both.

**Choice: JVM unit test only for this change.** The existing `dependency-injection` spec's final requirement says "This change MUST NOT introduce Hilt-aware tests" until a future test change explicitly needs them — which is a convention we should preserve. Also instrumented tests are resource-heavy and must not run on every PR (tracked as `nubecita-16a` with proper CI gating). Hilt compile-time graph validation (every `./gradlew assembleDebug`) catches wiring errors before runtime; the unit test proves the library call actually reaches Bluesky.

**Trade-off:** we're not literally proving `@Inject XrpcClient` works at runtime until `nubecita-16a` lands the instrumented counterpart. Acceptable given compile-time Hilt validation and that `MainActivity`'s existing injection flows will exercise `XrpcClient` once any feature consumes it.

### No helper abstraction over `XrpcClient`

Options considered:
- Inject `XrpcClient` directly.
- Introduce an `AtProtoClient` facade that wraps `XrpcClient` + commonly-paired `*Service` classes (`IdentityService`, `FeedService`, ...).

**Choice: inject `XrpcClient` directly.** The library's `*Service` classes are generated, thin wrappers around `XrpcClient.query` / `XrpcClient.procedure`; constructing `IdentityService(client)` at call-site is one line, and different VMs pair `XrpcClient` with different services. A facade would either be a trivial pass-through (noise) or grow to leak generated types (churn). If a genuine shared pattern emerges across ≥3 feature VMs, promote to a helper then.

## Risks / Trade-offs

- **Network-dependent unit test** → The `AtProtoClientTest.resolveHandle_bskyApp_returnsDid` test requires `public.api.bsky.app` to be reachable and the `bsky.app` handle to resolve. Mitigation: if CI flakes, the test can be `@Ignore`'d and the equivalent coverage moves to the instrumented test bucket landing in `nubecita-16a`. The upstream endpoint is operated by Bluesky and has been stable, so flakiness risk is low but non-zero.
- **Hard-coded `baseUrl`** → Production launch will likely need the URL behind a build-time source (flavors, `BuildConfig`). Mitigation: `nubecita-4g7` is the natural place to introduce that — authenticated flows need per-user PDS resolution anyway, so the URL-config concern arrives with real motivation.
- **Shared `HttpClient` coupling** → If we later install plugins for one flow (e.g., unauthenticated diagnostics) that must not apply to the other, we'd have to split the singleton. Mitigation: document the "no plugins on the shared `HttpClient`" rule in the module's comment header; revisit if a concrete need appears.
- **Ktor 3.0.0 pin** → The atproto-kotlin library's `atproto-setup` skill recommends 3.0.0; Ktor 3.x is on a fast release cadence and we may drift. Mitigation: Renovate will produce PRs; dependency alignment is part of the library's invariants, so we move Ktor's version together with the library's recommendation rather than leading.

## Migration Plan

Greenfield — no existing atproto-kotlin code paths to migrate. The one wrinkle:

1. Add the Ktor CIO coord to the catalog and `:app` module.
2. Drop in `AtProtoModule.kt`.
3. Drop in the unit test; confirm it passes.
4. `./gradlew assembleDebug spotlessCheck sortDependencies :app:dependencies --configuration releaseRuntimeClasspath` clean.
5. Open PR; squash-merge on green CI.

Rollback: a single revert commit. No data migration, no runtime state to unwind.

## Open Questions

None at this time. Base URL and engine choice are locked to match the library's own sample; test strategy is locked to match the existing `dependency-injection` spec's no-Hilt-tests rule.
